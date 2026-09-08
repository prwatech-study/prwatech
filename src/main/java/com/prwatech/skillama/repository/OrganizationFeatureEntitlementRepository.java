package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.OrganizationFeatureEntitlement;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationFeatureEntitlementRepository
        extends MongoRepository<OrganizationFeatureEntitlement, String> {
    List<OrganizationFeatureEntitlement> findByOrganizationId(String organizationId);

    Optional<OrganizationFeatureEntitlement> findByOrganizationIdAndFeatureCode(
            String organizationId, String featureCode);
}
