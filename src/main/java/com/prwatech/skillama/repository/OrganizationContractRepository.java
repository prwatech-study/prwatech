package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.OrganizationContract;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationContractRepository extends MongoRepository<OrganizationContract, String> {
    Optional<OrganizationContract> findByOrganizationId(String organizationId);

    List<OrganizationContract> findByStatus(OrganizationContract.ContractStatus status);

    List<OrganizationContract> findByStatusIn(java.util.Collection<OrganizationContract.ContractStatus> statuses);
}
