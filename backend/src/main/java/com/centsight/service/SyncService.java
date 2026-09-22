package com.centsight.service;

import com.centsight.domain.Account;
import com.centsight.domain.PlaidItem;
import com.centsight.domain.RecurringStream;
import com.centsight.domain.Txn;
import com.centsight.repo.AccountRepository;
import com.centsight.repo.PlaidItemRepository;
import com.centsight.repo.RecurringStreamRepository;
import com.centsight.repo.TxnRepository;
import com.plaid.client.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pulls balances, liabilities, transactions and recurring streams from Plaid into Postgres.
 * Port of the original Node sync, with every write scoped to the owning user.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    /** Products an institution simply may not offer. Not worth failing the whole sync over. */
    private static final Set<String> SOFT_ERRORS = Set.of(
            "PRODUCTS_NOT_SUPPORTED", "PRODUCT_NOT_READY", "NO_LIABILITY_ACCOUNTS", "ADDITIONAL_CONSENT_REQUIRED");

    /**
     * Self-reference used to call syncItem through the Spring proxy. Calling this.syncItem()
     * directly would bypass it, so @Transactional would not apply and the per-item writes would
     * run without a transaction.
     */
    private final ObjectProvider<SyncService> self;

    private final PlaidService plaid;
    private final CryptoService crypto;
    private final PlaidItemRepository items;
    private final AccountRepository accounts;
    private final TxnRepository txns;
    private final RecurringStreamRepository recurring;

    public SyncService(ObjectProvider<SyncService> self, PlaidService plaid, CryptoService crypto,
                       PlaidItemRepository items, AccountRepository accounts, TxnRepository txns,
                       RecurringStreamRepository recurring) {
        this.self = self;
        this.plaid = plaid;
        this.crypto = crypto;
        this.items = items;
        this.accounts = accounts;
        this.txns = txns;
        this.recurring = recurring;
    }

    public record SyncResult(String itemId, String institution, int added, int modified, int removed, String error) { }

    @Transactional
    public SyncResult syncItem(PlaidItem item) {
        String accessToken = crypto.decrypt(item.getAccessToken());

        String institution = syncAccounts(item, accessToken);
        if (institution != null) {
            item.setInstitutionName(institution);
        }
        syncLiabilities(item, accessToken);
        int[] counts = syncTransactions(item, accessToken);
        syncRecurring(item, accessToken);

        item.setLastSyncedAt(Instant.now());
        items.save(item);

        return new SyncResult(item.getItemId(), item.getInstitutionName(), counts[0], counts[1], counts[2], null);
    }

    public List<SyncResult> syncAll(UUID userId) {
        List<SyncResult> results = new ArrayList<>();
        for (PlaidItem item : items.findByUserIdOrderByCreatedAtAsc(userId)) {
            try {
                results.add(self.getObject().syncItem(item));
            } catch (PlaidException e) {
                log.error("sync failed for item {}: {}", item.getItemId(), e.getErrorCode());
                results.add(new SyncResult(item.getItemId(), item.getInstitutionName(), 0, 0, 0, e.getErrorCode()));
            }
        }
        return results;
    }

    // ---------- accounts ----------

    private String syncAccounts(PlaidItem item, String accessToken) {
        AccountsGetResponse res = plaid.call(
                plaid.api().accountsGet(new AccountsGetRequest().accessToken(accessToken)));

        for (AccountBase a : res.getAccounts()) {
            Account account = accounts.findById(a.getAccountId())
                    .orElseGet(() -> new Account(a.getAccountId(), item.getUserId(), item.getItemId()));

            account.setName(a.getName());
            account.setOfficialName(a.getOfficialName());
            account.setMask(a.getMask());
            account.setType(a.getType() == null ? null : a.getType().getValue());
            account.setSubtype(a.getSubtype() == null ? null : a.getSubtype().getValue());

            AccountBalance b = a.getBalances();
            if (b != null) {
                account.setCurrentBalance(toDecimal(b.getCurrent()));
                account.setAvailableBalance(toDecimal(b.getAvailable()));
                account.setCreditLimit(toDecimal(b.getLimit()));
                account.setCurrency(b.getIsoCurrencyCode() != null
                        ? b.getIsoCurrencyCode() : b.getUnofficialCurrencyCode());
            }
            account.setUpdatedAt(Instant.now());
            accounts.save(account);
        }

        return res.getItem() == null ? null : res.getItem().getInstitutionName();
    }

    // ---------- liabilities (statement, minimum, due date, APR) ----------

    private void syncLiabilities(PlaidItem item, String accessToken) {
        LiabilitiesGetResponse res;
        try {
            res = plaid.call(plaid.api().liabilitiesGet(new LiabilitiesGetRequest().accessToken(accessToken)));
        } catch (PlaidException e) {
            softFail(e, "liabilities");
            return;
        }
        if (res.getLiabilities() == null || res.getLiabilities().getCredit() == null) return;

        for (CreditCardLiability card : res.getLiabilities().getCredit()) {
            accounts.findByAccountIdAndUserId(card.getAccountId(), item.getUserId()).ifPresent(account -> {
                account.setStatementBalance(toDecimal(card.getLastStatementBalance()));
                account.setMinPayment(toDecimal(card.getMinimumPaymentAmount()));
                account.setDueDate(card.getNextPaymentDueDate());

                if (card.getAprs() != null && !card.getAprs().isEmpty()) {
                    APR purchase = card.getAprs().stream()
                            .filter(x -> x.getAprType() == APR.AprTypeEnum.PURCHASE_APR)
                            .findFirst()
                            .orElse(card.getAprs().get(0));
                    account.setApr(toDecimal(purchase.getAprPercentage()));
                }
                accounts.save(account);
            });
        }
    }

    // ---------- transactions ----------

    private int[] syncTransactions(PlaidItem item, String accessToken) {
        String startCursor = item.getCursor();
        String cursor = startCursor;

        List<Transaction> added = new ArrayList<>();
        List<Transaction> modified = new ArrayList<>();
        List<RemovedTransaction> removed = new ArrayList<>();
        boolean hasMore = true;

        while (hasMore) {
            TransactionsSyncRequest req = new TransactionsSyncRequest().accessToken(accessToken).count(500);
            if (cursor != null) req.setCursor(cursor);

            TransactionsSyncResponse page;
            try {
                page = plaid.call(plaid.api().transactionsSync(req));
            } catch (PlaidException e) {
                if ("TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION".equals(e.getErrorCode())) {
                    // Data moved under us: restart from the last saved cursor.
                    cursor = startCursor;
                    added.clear();
                    modified.clear();
                    removed.clear();
                    continue;
                }
                throw e;
            }

            added.addAll(page.getAdded());
            modified.addAll(page.getModified());
            removed.addAll(page.getRemoved());
            cursor = page.getNextCursor();
            hasMore = Boolean.TRUE.equals(page.getHasMore());
        }

        List<Transaction> upserts = new ArrayList<>(added);
        upserts.addAll(modified);
        for (Transaction t : upserts) {
            Txn txn = txns.findById(t.getTransactionId())
                    .orElseGet(() -> new Txn(t.getTransactionId(), item.getUserId(), item.getItemId(), t.getAccountId()));
            txn.setAccountId(t.getAccountId());
            txn.setDate(t.getDate());
            txn.setName(t.getName());
            txn.setMerchantName(t.getMerchantName());
            txn.setAmount(toDecimal(t.getAmount()));
            PersonalFinanceCategory pfc = t.getPersonalFinanceCategory();
            txn.setCategory(pfc == null ? null : pfc.getPrimary());
            txn.setCategoryDetailed(pfc == null ? null : pfc.getDetailed());
            txn.setPending(Boolean.TRUE.equals(t.getPending()));
            txn.setLogoUrl(t.getLogoUrl());
            txns.save(txn);
        }

        for (RemovedTransaction r : removed) {
            txns.deleteById(r.getTransactionId());
        }

        item.setCursor(cursor);
        return new int[]{added.size(), modified.size(), removed.size()};
    }

    // ---------- recurring ----------

    private void syncRecurring(PlaidItem item, String accessToken) {
        TransactionsRecurringGetResponse res;
        try {
            res = plaid.call(plaid.api().transactionsRecurringGet(
                    new TransactionsRecurringGetRequest().accessToken(accessToken)));
        } catch (PlaidException e) {
            softFail(e, "recurring");
            return;
        }

        recurring.deleteByItemId(item.getItemId());
        recurring.flush();

        saveStreams(item, res.getInflowStreams(), "inflow");
        saveStreams(item, res.getOutflowStreams(), "outflow");
    }

    private void saveStreams(PlaidItem item, List<TransactionStream> streams, String direction) {
        if (streams == null) return;
        for (TransactionStream s : streams) {
            RecurringStream row = new RecurringStream(s.getStreamId(), item.getUserId(), item.getItemId(), direction);
            row.setAccountId(s.getAccountId());
            row.setDescription(s.getDescription());
            row.setMerchantName(s.getMerchantName());
            row.setFrequency(s.getFrequency() == null ? null : s.getFrequency().getValue());
            row.setAverageAmount(absAmount(s.getAverageAmount()));
            row.setLastAmount(absAmount(s.getLastAmount()));
            row.setLastDate(s.getLastDate());
            row.setNextDate(s.getPredictedNextDate());
            row.setActive(Boolean.TRUE.equals(s.getIsActive()));
            row.setStatus(s.getStatus() == null ? null : s.getStatus().getValue());
            PersonalFinanceCategory pfc = s.getPersonalFinanceCategory();
            row.setCategory(pfc == null ? null : pfc.getPrimary());
            recurring.save(row);
        }
    }

    // ---------- helpers ----------

    private void softFail(PlaidException e, String label) {
        if (SOFT_ERRORS.contains(e.getErrorCode())) {
            log.info("sync {}: skipped ({})", label, e.getErrorCode());
            return;
        }
        throw e;
    }

    private static BigDecimal toDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static BigDecimal absAmount(TransactionStreamAmount amount) {
        if (amount == null || amount.getAmount() == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.abs(amount.getAmount()));
    }

    /** Frequency -> how many times it happens in a month. */
    public static final Map<String, BigDecimal> MONTHLY_FACTOR = Map.of(
            "WEEKLY", BigDecimal.valueOf(52).divide(BigDecimal.valueOf(12), 6, java.math.RoundingMode.HALF_UP),
            "BIWEEKLY", BigDecimal.valueOf(26).divide(BigDecimal.valueOf(12), 6, java.math.RoundingMode.HALF_UP),
            "SEMI_MONTHLY", BigDecimal.valueOf(2),
            "MONTHLY", BigDecimal.ONE,
            "ANNUALLY", BigDecimal.ONE.divide(BigDecimal.valueOf(12), 6, java.math.RoundingMode.HALF_UP));

    public static LocalDate monthStart(String yearMonth) {
        return LocalDate.parse(yearMonth + "-01");
    }
}
