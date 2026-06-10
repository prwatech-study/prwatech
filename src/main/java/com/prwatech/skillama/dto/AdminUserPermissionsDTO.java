package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminUserPermissionsDTO {
    private String userId;
    private String name;
    private String email;
    private String role;
    /** true when using legacy full-access defaults (no explicit grants stored). */
    private boolean legacyFullAccess;
    private List<AdminModulePermissionDTO> permissions;
}
