package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.OrganizationContractRenewal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrganizationContractRenewalRepository
        extends MongoRepository<OrganizationContractRenewal, String> {
    List<OrganizationContractRenewal> findByOrganizationIdOrderByRenewedAtDesc(String organizationId);
}
