package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.OrganizationActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrganizationActivityLogRepository
        extends MongoRepository<OrganizationActivityLog, String> {
    Page<OrganizationActivityLog> findByOrganizationIdOrderByCreatedAtDesc(
            String organizationId, Pageable pageable);
}
