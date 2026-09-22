package com.centsight.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurring_streams")
public class RecurringStream {

    @Id
    @Column(name = "stream_id")
    private String streamId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Column(name = "account_id")
    private String accountId;

    /** inflow | outflow */
    @Column(nullable = false)
    private String direction;

    private String description;

    @Column(name = "merchant_name")
    private String merchantName;

    private String frequency;

    @Column(name = "average_amount")
    private BigDecimal averageAmount;

    @Column(name = "last_amount")
    private BigDecimal lastAmount;

    @Column(name = "last_date")
    private LocalDate lastDate;

    @Column(name = "next_date")
    private LocalDate nextDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    private String status;
    private String category;

    protected RecurringStream() { }

    public RecurringStream(String streamId, UUID userId, String itemId, String direction) {
        this.streamId = streamId;
        this.userId = userId;
        this.itemId = itemId;
        this.direction = direction;
    }

    public String getStreamId() { return streamId; }
    public UUID getUserId() { return userId; }
    public String getItemId() { return itemId; }
    public String getAccountId() { return accountId; }
    public String getDirection() { return direction; }
    public String getDescription() { return description; }
    public String getMerchantName() { return merchantName; }
    public String getFrequency() { return frequency; }
    public BigDecimal getAverageAmount() { return averageAmount; }
    public BigDecimal getLastAmount() { return lastAmount; }
    public LocalDate getLastDate() { return lastDate; }
    public LocalDate getNextDate() { return nextDate; }
    public boolean isActive() { return active; }
    public String getStatus() { return status; }
    public String getCategory() { return category; }

    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setDescription(String description) { this.description = description; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public void setAverageAmount(BigDecimal v) { this.averageAmount = v; }
    public void setLastAmount(BigDecimal v) { this.lastAmount = v; }
    public void setLastDate(LocalDate v) { this.lastDate = v; }
    public void setNextDate(LocalDate v) { this.nextDate = v; }
    public void setActive(boolean active) { this.active = active; }
    public void setStatus(String status) { this.status = status; }
    public void setCategory(String category) { this.category = category; }
}
