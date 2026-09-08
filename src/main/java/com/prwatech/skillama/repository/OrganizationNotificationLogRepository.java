package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.OrganizationNotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrganizationNotificationLogRepository
        extends MongoRepository<OrganizationNotificationLog, String> {
    Page<OrganizationNotificationLog> findByOrganizationIdOrderBySentAtDesc(
            String organizationId, Pageable pageable);
}
