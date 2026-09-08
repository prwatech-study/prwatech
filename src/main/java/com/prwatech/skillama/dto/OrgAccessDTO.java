package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgAccessDTO {
    private boolean hasAccess;
    private String orgRole;
    private boolean owner;
    /** true when ORG_ADMIN has no explicit grants (full access until owner restricts). */
    private boolean legacyFullAccess;
    @Builder.Default
    private List<OrgModulePermissionDTO> modulePermissions = new ArrayList<>();
}
