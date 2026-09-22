package com.centsight.web;

import com.centsight.domain.PlaidItem;
import com.centsight.domain.User;
import com.centsight.dto.Dtos.ExchangeRequest;
import com.centsight.dto.Dtos.LinkToken;
import com.centsight.repo.AccountRepository;
import com.centsight.repo.PlaidItemRepository;
import com.centsight.repo.RecurringStreamRepository;
import com.centsight.repo.TxnRepository;
import com.centsight.service.CryptoService;
import com.centsight.service.PlaidService;
import com.centsight.service.SyncService;
import com.plaid.client.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Plaid Link: mint a link token, exchange the public token, list and remove connections. */
@RestController
@RequestMapping("/api")
public class LinkController {

    private final PlaidService plaid;
    private final CryptoService crypto;
    private final SyncService sync;
    private final PlaidItemRepository items;
    private final AccountRepository accounts;
    private final TxnRepository txns;
    private final RecurringStreamRepository recurring;
    private final String webhookUrl;

    public LinkController(PlaidService plaid, CryptoService crypto, SyncService sync,
                          PlaidItemRepository items, AccountRepository accounts,
                          TxnRepository txns, RecurringStreamRepository recurring,
                          @Value("${centsight.plaid.webhook-url:}") String webhookUrl) {
        this.plaid = plaid;
        this.crypto = crypto;
        this.sync = sync;
        this.items = items;
        this.accounts = accounts;
        this.txns = txns;
        this.recurring = recurring;
        this.webhookUrl = webhookUrl;
    }

    @PostMapping("/link/token")
    public LinkToken createLinkToken(User user) {
        // client_user_id is the real user id, so Plaid ties each Item to the person who linked it.
        LinkTokenCreateRequest req = new LinkTokenCreateRequest()
                .user(new LinkTokenCreateRequestUser().clientUserId(user.getId().toString()))
                .clientName("CentSight")
                .products(List.of(Products.TRANSACTIONS))
                .optionalProducts(List.of(Products.LIABILITIES))
                .countryCodes(List.of(CountryCode.US))
                .language("en")
                .transactions(new LinkTokenTransactions().daysRequested(730));

        if (!webhookUrl.isBlank()) req.setWebhook(webhookUrl);

        LinkTokenCreateResponse res = plaid.call(plaid.api().linkTokenCreate(req));
        return new LinkToken(res.getLinkToken());
    }

    @PostMapping("/link/exchange")
    @Transactional
    public SyncService.SyncResult exchange(User user, @RequestBody ExchangeRequest body) {
        if (body.publicToken() == null || body.publicToken().isBlank()) {
            throw new BadRequestException("public_token is required");
        }

        ItemPublicTokenExchangeResponse res = plaid.call(plaid.api().itemPublicTokenExchange(
                new ItemPublicTokenExchangeRequest().publicToken(body.publicToken())));

        PlaidItem item = items.findById(res.getItemId())
                .map(existing -> {
                    if (!existing.getUserId().equals(user.getId())) {
                        // This Item belongs to somebody else's account; refuse rather than reassign it.
                        throw new BadRequestException("That connection is already linked to another account");
                    }
                    existing.setAccessToken(crypto.encrypt(res.getAccessToken()));
                    existing.setInstitutionName(body.institutionName());
                    return existing;
                })
                .orElseGet(() -> new PlaidItem(res.getItemId(), user.getId(),
                        crypto.encrypt(res.getAccessToken()), body.institutionName()));

        items.save(item);
        return sync.syncItem(item);
    }

    @PostMapping("/sync")
    public List<SyncService.SyncResult> sync(User user) {
        return sync.syncAll(user.getId());
    }

    @DeleteMapping("/items/{itemId}")
    @Transactional
    public Map<String, String> removeItem(User user, @PathVariable String itemId) {
        PlaidItem item = items.findByItemIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new NotFoundException("Connection not found"));

        plaid.call(plaid.api().itemRemove(
                new ItemRemoveRequest().accessToken(crypto.decrypt(item.getAccessToken()))));

        recurring.deleteByItemId(itemId);
        txns.deleteByItemId(itemId);
        accounts.deleteByItemId(itemId);
        items.delete(item);

        return Map.of("removed", itemId);
    }
}
