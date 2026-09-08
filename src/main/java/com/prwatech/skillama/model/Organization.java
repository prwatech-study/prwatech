package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organizations")
public class Organization {
    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String slug;

    @Indexed
    private String customDomain;

    @Indexed
    private OrganizationStatus status;

    @Indexed
    private String rootOwnerUserId;

    private String currentContractId;

    private String contactEmail;
    private String salesContactEmail;

    @Builder.Default
    private OrganizationBranding branding = new OrganizationBranding();

    @Builder.Default
    private OrganizationSecurity security = new OrganizationSecurity();

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
}
