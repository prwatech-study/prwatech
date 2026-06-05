package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CreditAdjustmentLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CreditAdjustmentLogRepository extends MongoRepository<CreditAdjustmentLog, String> {
    Page<CreditAdjustmentLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<CreditAdjustmentLog> findByUserIdOrderByCreatedAtAsc(String userId);
}
