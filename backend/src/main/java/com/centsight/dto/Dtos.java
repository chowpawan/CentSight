package com.centsight.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response shapes for the dashboard. Jackson is configured for snake_case,
 * so `netWorth` goes out as `net_worth` and the existing React client needs no changes.
 */
public final class Dtos {

    private Dtos() { }

    public record AccountView(
            String accountId, String itemId, String name, String officialName, String mask,
            String type, String subtype,
            BigDecimal current, BigDecimal available, BigDecimal creditLimit, String currency,
            BigDecimal statementBalance, BigDecimal minPayment, LocalDate dueDate, BigDecimal apr,
            Instant updatedAt, String institutionName) { }

    public record Summary(BigDecimal assets, BigDecimal debts, BigDecimal netWorth, List<AccountView> accounts) { }

    public record CategorySpend(String category, BigDecimal total, long count) { }

    public record MerchantSpend(String merchant, BigDecimal total, long count, String logoUrl) { }

    public record TrendPoint(String month, BigDecimal total) { }

    public record Spending(String month, BigDecimal total, BigDecimal income,
                           List<CategorySpend> byCategory, List<MerchantSpend> topMerchants,
                           List<TrendPoint> trend) { }

    public record StreamView(String streamId, String description, String merchantName, String category,
                             String frequency, BigDecimal averageAmount, BigDecimal lastAmount,
                             LocalDate nextDate, BigDecimal monthly, String accountName, String mask) { }

    public record Recurring(List<StreamView> outflows, List<StreamView> inflows,
                            BigDecimal monthlyOut, BigDecimal monthlyIn) { }

    public record TxnView(String transactionId, LocalDate date, String name, String merchantName,
                          BigDecimal amount, String category, boolean pending,
                          String logoUrl, String accountName, String mask) { }

    public record ItemView(String itemId, String institutionName, Instant createdAt, Instant lastSyncedAt) { }

    public record Me(String id, String email, String name, String pictureUrl) { }

    public record LinkToken(String linkToken) { }

    public record ExchangeRequest(String publicToken, String institutionName) { }

    public record ApiError(String errorCode, String errorMessage) { }
}
