package com.centsight.web;

import com.centsight.domain.PlaidItem;
import com.centsight.repo.PlaidItemRepository;
import com.centsight.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Plaid calls this when new data is ready. Unauthenticated by necessity, so it only ever
 * triggers a sync for an item id we already hold — it never returns data.
 *
 * Before production, verify the Plaid-Verification JWT header:
 * https://plaid.com/docs/api/webhooks/webhook-verification/
 */
@RestController
@RequestMapping("/api")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private static final Set<String> TRANSACTION_CODES =
            Set.of("SYNC_UPDATES_AVAILABLE", "RECURRING_TRANSACTIONS_UPDATE");

    private final PlaidItemRepository items;
    private final SyncService sync;

    public WebhookController(PlaidItemRepository items, SyncService sync) {
        this.items = items;
        this.sync = sync;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.get("webhook_type"));
        String code = String.valueOf(body.get("webhook_code"));
        String itemId = String.valueOf(body.get("item_id"));

        boolean shouldSync = ("TRANSACTIONS".equals(type) && TRANSACTION_CODES.contains(code))
                || ("LIABILITIES".equals(type) && "DEFAULT_UPDATE".equals(code));

        if (shouldSync) {
            items.findById(itemId).ifPresent(this::syncQuietly);
        }
        return ResponseEntity.ok().build(); // acknowledge fast
    }

    @Async
    void syncQuietly(PlaidItem item) {
        try {
            sync.syncItem(item);
        } catch (RuntimeException e) {
            log.error("webhook sync failed for item {}", item.getItemId(), e);
        }
    }
}
