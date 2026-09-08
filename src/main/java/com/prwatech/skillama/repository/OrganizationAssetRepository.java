package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.OrgAssetKind;
import com.prwatech.skillama.model.OrganizationAsset;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationAssetRepository extends MongoRepository<OrganizationAsset, String> {
    Optional<OrganizationAsset> findFirstByOrganizationIdAndKindAndActiveTrue(
            String organizationId, OrgAssetKind kind);

    List<OrganizationAsset> findByOrganizationIdAndKindOrderByUploadedAtDesc(
            String organizationId, OrgAssetKind kind);
}
