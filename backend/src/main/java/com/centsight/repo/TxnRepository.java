package com.centsight.repo;

import com.centsight.domain.Txn;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TxnRepository extends JpaRepository<Txn, String> {

    List<Txn> findByUserIdOrderByDateDescTransactionIdAsc(UUID userId, Pageable page);

    void deleteByItemId(String itemId);

    @Query("""
        SELECT DISTINCT FUNCTION('to_char', t.date, 'YYYY-MM')
        FROM Txn t WHERE t.userId = :userId
        ORDER BY 1 DESC
        """)
    List<String> findMonths(@Param("userId") UUID userId);

    /** Spending rows for a month: money out, posted, excluding transfers between your own accounts. */
    @Query("""
        SELECT COALESCE(t.category, 'OTHER'), SUM(t.amount), COUNT(t)
        FROM Txn t
        WHERE t.userId = :userId AND t.amount > 0 AND t.pending = false
          AND t.date >= :from AND t.date < :to
          AND COALESCE(t.category, '') NOT IN :excluded
        GROUP BY COALESCE(t.category, 'OTHER')
        ORDER BY SUM(t.amount) DESC
        """)
    List<Object[]> spendingByCategory(@Param("userId") UUID userId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("excluded") Collection<String> excluded);

    @Query("""
        SELECT COALESCE(t.merchantName, t.name), SUM(t.amount), COUNT(t), MAX(t.logoUrl)
        FROM Txn t
        WHERE t.userId = :userId AND t.amount > 0 AND t.pending = false
          AND t.date >= :from AND t.date < :to
          AND COALESCE(t.category, '') NOT IN :excluded
        GROUP BY COALESCE(t.merchantName, t.name)
        ORDER BY SUM(t.amount) DESC
        """)
    List<Object[]> topMerchants(@Param("userId") UUID userId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                @Param("excluded") Collection<String> excluded,
                                Pageable page);

    @Query("""
        SELECT COALESCE(SUM(-t.amount), 0)
        FROM Txn t
        WHERE t.userId = :userId AND t.amount < 0 AND t.category = 'INCOME'
          AND t.date >= :from AND t.date < :to
        """)
    BigDecimal incomeFor(@Param("userId") UUID userId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    /** Monthly spend totals up to and including the given month, newest first. */
    @Query("""
        SELECT FUNCTION('to_char', t.date, 'YYYY-MM'), SUM(t.amount)
        FROM Txn t
        WHERE t.userId = :userId AND t.amount > 0 AND t.pending = false
          AND t.date < :before
          AND COALESCE(t.category, '') NOT IN :excluded
        GROUP BY FUNCTION('to_char', t.date, 'YYYY-MM')
        ORDER BY 1 DESC
        """)
    List<Object[]> monthlyTrend(@Param("userId") UUID userId,
                                @Param("before") LocalDate before,
                                @Param("excluded") Collection<String> excluded,
                                Pageable page);
}
