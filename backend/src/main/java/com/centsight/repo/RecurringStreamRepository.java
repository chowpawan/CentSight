package com.centsight.repo;

import com.centsight.domain.RecurringStream;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RecurringStreamRepository extends JpaRepository<RecurringStream, String> {

    List<RecurringStream> findByUserIdAndActiveTrueOrderByAverageAmountDesc(UUID userId);

    void deleteByItemId(String itemId);
}
