package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminAuditLogRepository extends MongoRepository<AdminAuditLog, String> {
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AdminAuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    Page<AdminAuditLog> findByActorIdOrderByCreatedAtDesc(String actorId, Pageable pageable);
}
