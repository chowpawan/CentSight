package com.centsight.repo;

import com.centsight.domain.PlaidItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaidItemRepository extends JpaRepository<PlaidItem, String> {
    List<PlaidItem> findByUserIdOrderByCreatedAtAsc(UUID userId);

    /** Scoped lookup: an item is only ever readable by the user who linked it. */
    Optional<PlaidItem> findByItemIdAndUserId(String itemId, UUID userId);
}
