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
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organization_feature_entitlements")
@CompoundIndex(name = "org_feature_unique", def = "{'organizationId': 1, 'featureCode': 1}", unique = true)
public class OrganizationFeatureEntitlement {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    @Indexed
    private String featureCode;

    @Builder.Default
    private boolean enabled = true;

    private Map<String, Object> configValue;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String grantedBy;
    private LocalDateTime grantedAt;
    private String notes;
}
