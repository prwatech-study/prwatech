package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OrgAdminPermissionsDTO {
    private String userId;
    private String name;
    private String email;
    private boolean legacyFullAccess;
    private List<OrgModulePermissionDTO> permissions;
}
