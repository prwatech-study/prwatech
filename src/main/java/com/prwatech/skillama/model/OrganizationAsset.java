package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organization_assets")
@CompoundIndex(name = "org_asset_kind_active", def = "{'organizationId': 1, 'kind': 1, 'active': 1}")
public class OrganizationAsset {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    private String slug;
    private OrgAssetKind kind;
    private String s3Bucket;
    private String s3Key;
    private String url;
    private String fileName;
    private String contentType;
    private Long fileSize;

    @Builder.Default
    private boolean active = true;

    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
