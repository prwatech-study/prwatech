package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.OrganizationDepartment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationDepartmentRepository
        extends MongoRepository<OrganizationDepartment, String> {

    List<OrganizationDepartment> findByOrganizationIdOrderByNameAsc(String organizationId);

    Optional<OrganizationDepartment> findByOrganizationIdAndCodeIgnoreCase(String organizationId, String code);

    Optional<OrganizationDepartment> findByOrganizationIdAndCatalogCode(String organizationId, String catalogCode);
}
