package com.centsight.repo;

import com.centsight.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, String> {
    List<Account> findByUserId(UUID userId);
    Optional<Account> findByAccountIdAndUserId(String accountId, UUID userId);
    void deleteByItemId(String itemId);
}
