package com.centsight.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plaid_items")
public class PlaidItem {

    @Id
    @Column(name = "item_id")
    private String itemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** AES-256-GCM ciphertext. Never holds a plaintext Plaid access token. */
    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "institution_name")
    private String institutionName;

    @Column(name = "cursor")
    private String cursor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    protected PlaidItem() { }

    public PlaidItem(String itemId, UUID userId, String accessToken, String institutionName) {
        this.itemId = itemId;
        this.userId = userId;
        this.accessToken = accessToken;
        this.institutionName = institutionName;
        this.createdAt = Instant.now();
    }

    public String getItemId() { return itemId; }
    public UUID getUserId() { return userId; }
    public String getAccessToken() { return accessToken; }
    public String getInstitutionName() { return institutionName; }
    public String getCursor() { return cursor; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }

    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }
    public void setCursor(String cursor) { this.cursor = cursor; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
