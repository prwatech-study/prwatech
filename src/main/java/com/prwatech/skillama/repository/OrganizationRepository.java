package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends MongoRepository<Organization, String> {
    Optional<Organization> findBySlug(String slug);

    Optional<Organization> findByCustomDomain(String customDomain);

    boolean existsBySlug(String slug);

    Page<Organization> findByStatus(OrganizationStatus status, Pageable pageable);

    List<Organization> findBySecurityAllowedEmailDomainsContaining(String domain);
}
