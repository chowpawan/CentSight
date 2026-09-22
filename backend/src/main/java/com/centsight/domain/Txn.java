package com.centsight.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A Plaid transaction. Amount follows Plaid's sign convention: positive = money leaving the account. */
@Entity
@Table(name = "transactions")
public class Txn {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "tx_date", nullable = false)
    private LocalDate date;

    private String name;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(nullable = false)
    private BigDecimal amount;

    private String category;

    @Column(name = "category_detailed")
    private String categoryDetailed;

    @Column(nullable = false)
    private boolean pending;

    @Column(name = "logo_url")
    private String logoUrl;

    protected Txn() { }

    public Txn(String transactionId, UUID userId, String itemId, String accountId) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.itemId = itemId;
        this.accountId = accountId;
    }

    public String getTransactionId() { return transactionId; }
    public UUID getUserId() { return userId; }
    public String getItemId() { return itemId; }
    public String getAccountId() { return accountId; }
    public LocalDate getDate() { return date; }
    public String getName() { return name; }
    public String getMerchantName() { return merchantName; }
    public BigDecimal getAmount() { return amount; }
    public String getCategory() { return category; }
    public String getCategoryDetailed() { return categoryDetailed; }
    public boolean isPending() { return pending; }
    public String getLogoUrl() { return logoUrl; }

    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setDate(LocalDate date) { this.date = date; }
    public void setName(String name) { this.name = name; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCategory(String category) { this.category = category; }
    public void setCategoryDetailed(String categoryDetailed) { this.categoryDetailed = categoryDetailed; }
    public void setPending(boolean pending) { this.pending = pending; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
}
