package com.centsight.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    private String name;

    @Column(name = "official_name")
    private String officialName;

    private String mask;
    private String type;
    private String subtype;

    @Column(name = "current_balance")
    private BigDecimal currentBalance;

    @Column(name = "available_balance")
    private BigDecimal availableBalance;

    @Column(name = "credit_limit")
    private BigDecimal creditLimit;

    private String currency;

    @Column(name = "statement_balance")
    private BigDecimal statementBalance;

    @Column(name = "min_payment")
    private BigDecimal minPayment;

    @Column(name = "due_date")
    private LocalDate dueDate;

    private BigDecimal apr;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Account() { }

    public Account(String accountId, UUID userId, String itemId) {
        this.accountId = accountId;
        this.userId = userId;
        this.itemId = itemId;
    }

    public String getAccountId() { return accountId; }
    public UUID getUserId() { return userId; }
    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public String getOfficialName() { return officialName; }
    public String getMask() { return mask; }
    public String getType() { return type; }
    public String getSubtype() { return subtype; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public String getCurrency() { return currency; }
    public BigDecimal getStatementBalance() { return statementBalance; }
    public BigDecimal getMinPayment() { return minPayment; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getApr() { return apr; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setOfficialName(String officialName) { this.officialName = officialName; }
    public void setMask(String mask) { this.mask = mask; }
    public void setType(String type) { this.type = type; }
    public void setSubtype(String subtype) { this.subtype = subtype; }
    public void setCurrentBalance(BigDecimal v) { this.currentBalance = v; }
    public void setAvailableBalance(BigDecimal v) { this.availableBalance = v; }
    public void setCreditLimit(BigDecimal v) { this.creditLimit = v; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setStatementBalance(BigDecimal v) { this.statementBalance = v; }
    public void setMinPayment(BigDecimal v) { this.minPayment = v; }
    public void setDueDate(LocalDate v) { this.dueDate = v; }
    public void setApr(BigDecimal v) { this.apr = v; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
