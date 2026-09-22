package com.centsight.service;

import com.centsight.domain.Account;
import com.centsight.domain.PlaidItem;
import com.centsight.domain.RecurringStream;
import com.centsight.domain.Txn;
import com.centsight.dto.Dtos.*;
import com.centsight.repo.AccountRepository;
import com.centsight.repo.PlaidItemRepository;
import com.centsight.repo.RecurringStreamRepository;
import com.centsight.repo.TxnRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Read-side queries for the dashboard. Every method takes a userId and filters on it. */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    /** Money moving between your own accounts is not spending, so paying a card isn't counted twice. */
    private static final List<String> NOT_SPENDING =
            List.of("TRANSFER_IN", "TRANSFER_OUT", "LOAN_PAYMENTS", "INCOME", "BANK_FEES_REFUND");

    private static final Set<String> DEBT_TYPES = Set.of("credit", "loan");

    private final AccountRepository accounts;
    private final TxnRepository txns;
    private final RecurringStreamRepository recurring;
    private final PlaidItemRepository items;

    public AnalyticsService(AccountRepository accounts, TxnRepository txns,
                            RecurringStreamRepository recurring, PlaidItemRepository items) {
        this.accounts = accounts;
        this.txns = txns;
        this.recurring = recurring;
        this.items = items;
    }

    public Summary summary(UUID userId) {
        Map<String, String> institutions = new HashMap<>();
        for (PlaidItem item : items.findByUserIdOrderByCreatedAtAsc(userId)) {
            institutions.put(item.getItemId(), item.getInstitutionName());
        }

        List<Account> rows = new ArrayList<>(accounts.findByUserId(userId));
        rows.sort(Comparator
                .comparing((Account a) -> nullSafe(a.getType()))
                .thenComparing(a -> nullSafe(institutions.get(a.getItemId())))
                .thenComparing(a -> nullSafe(a.getName())));

        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal debts = BigDecimal.ZERO;
        List<AccountView> views = new ArrayList<>(rows.size());

        for (Account a : rows) {
            BigDecimal balance = a.getCurrentBalance() == null ? BigDecimal.ZERO : a.getCurrentBalance();
            if (DEBT_TYPES.contains(nullSafe(a.getType()))) debts = debts.add(balance);
            else assets = assets.add(balance);

            views.add(new AccountView(
                    a.getAccountId(), a.getItemId(), a.getName(), a.getOfficialName(), a.getMask(),
                    a.getType(), a.getSubtype(), a.getCurrentBalance(), a.getAvailableBalance(),
                    a.getCreditLimit(), a.getCurrency(), a.getStatementBalance(), a.getMinPayment(),
                    a.getDueDate(), a.getApr(), a.getUpdatedAt(), institutions.get(a.getItemId())));
        }

        return new Summary(assets, debts, assets.subtract(debts), views);
    }

    public List<String> months(UUID userId) {
        return txns.findMonths(userId);
    }

    public Spending spending(UUID userId, String month) {
        LocalDate from = SyncService.monthStart(month);
        LocalDate to = from.plusMonths(1);

        List<CategorySpend> byCategory = txns.spendingByCategory(userId, from, to, NOT_SPENDING).stream()
                .map(r -> new CategorySpend((String) r[0], (BigDecimal) r[1], ((Number) r[2]).longValue()))
                .toList();

        List<MerchantSpend> topMerchants = txns.topMerchants(userId, from, to, NOT_SPENDING, PageRequest.of(0, 8)).stream()
                .map(r -> new MerchantSpend((String) r[0], (BigDecimal) r[1], ((Number) r[2]).longValue(), (String) r[3]))
                .toList();

        BigDecimal income = orZero(txns.incomeFor(userId, from, to));

        // Newest-first from the query, reversed so the chart reads left to right.
        List<TrendPoint> trend = new ArrayList<>(
                txns.monthlyTrend(userId, to, NOT_SPENDING, PageRequest.of(0, 6)).stream()
                        .map(r -> new TrendPoint((String) r[0], (BigDecimal) r[1]))
                        .toList());
        Collections.reverse(trend);

        BigDecimal total = byCategory.stream().map(CategorySpend::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Spending(month, total, income, byCategory, topMerchants, trend);
    }

    public Recurring recurring(UUID userId) {
        Map<String, Account> byId = new HashMap<>();
        for (Account a : accounts.findByUserId(userId)) byId.put(a.getAccountId(), a);

        List<StreamView> outflows = new ArrayList<>();
        List<StreamView> inflows = new ArrayList<>();

        for (RecurringStream s : recurring.findByUserIdAndActiveTrueOrderByAverageAmountDesc(userId)) {
            if ("TOMBSTONED".equals(s.getStatus())) continue;

            BigDecimal avg = orZero(s.getAverageAmount());
            BigDecimal factor = SyncService.MONTHLY_FACTOR.getOrDefault(nullSafe(s.getFrequency()), BigDecimal.ONE);
            BigDecimal monthly = avg.multiply(factor).setScale(2, RoundingMode.HALF_UP);

            Account account = s.getAccountId() == null ? null : byId.get(s.getAccountId());
            StreamView view = new StreamView(
                    s.getStreamId(), s.getDescription(), s.getMerchantName(), s.getCategory(),
                    s.getFrequency(), avg, s.getLastAmount(), s.getNextDate(), monthly,
                    account == null ? null : account.getName(),
                    account == null ? null : account.getMask());

            if ("inflow".equals(s.getDirection())) inflows.add(view);
            else outflows.add(view);
        }

        return new Recurring(outflows, inflows, sumMonthly(outflows), sumMonthly(inflows));
    }

    public List<TxnView> transactions(UUID userId, int limit) {
        Map<String, Account> byId = new HashMap<>();
        for (Account a : accounts.findByUserId(userId)) byId.put(a.getAccountId(), a);

        return txns.findByUserIdOrderByDateDescTransactionIdAsc(userId, PageRequest.of(0, limit)).stream()
                .map(t -> {
                    Account a = byId.get(t.getAccountId());
                    return new TxnView(t.getTransactionId(), t.getDate(), t.getName(), t.getMerchantName(),
                            t.getAmount(), t.getCategory(), t.isPending(), t.getLogoUrl(),
                            a == null ? null : a.getName(), a == null ? null : a.getMask());
                })
                .toList();
    }

    public List<ItemView> items(UUID userId) {
        return items.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(i -> new ItemView(i.getItemId(), i.getInstitutionName(), i.getCreatedAt(), i.getLastSyncedAt()))
                .toList();
    }

    private static BigDecimal sumMonthly(List<StreamView> rows) {
        return rows.stream().map(StreamView::monthly).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
