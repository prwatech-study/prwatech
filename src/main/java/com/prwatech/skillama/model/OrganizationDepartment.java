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

/**
 * Per-organization department. Custom rows are created by the org owner;
 * catalog rows are enabled copies of the platform defaults.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organization_departments")
@CompoundIndex(name = "org_dept_code", def = "{'organizationId': 1, 'code': 1}", unique = true)
public class OrganizationDepartment {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    /** Stable slug stored on users, e.g. naukri-tech. */
    private String code;

    /** Display name, e.g. Naukri Tech. */
    private String name;

    /** Set when this row was enabled from the default catalog. */
    private String catalogCode;

    @Builder.Default
    private boolean fromCatalog = false;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
}
