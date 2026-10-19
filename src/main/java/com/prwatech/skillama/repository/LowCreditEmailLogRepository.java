package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.LowCreditEmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LowCreditEmailLogRepository extends MongoRepository<LowCreditEmailLog, String> {

    Page<LowCreditEmailLog> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    Page<LowCreditEmailLog> findByStatusOrderByUpdatedAtDesc(
            LowCreditEmailLog.Status status, Pageable pageable);
}
