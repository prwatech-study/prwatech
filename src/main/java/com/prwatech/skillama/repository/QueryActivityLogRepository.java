package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.QueryActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface QueryActivityLogRepository extends MongoRepository<QueryActivityLog, String> {
    Page<QueryActivityLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserId(String userId);
}
