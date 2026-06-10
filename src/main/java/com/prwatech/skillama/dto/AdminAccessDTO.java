package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccessDTO {
    private Boolean hasAccess;
    private String role;
    /** @deprecated use modulePermissions */
    private List<String> permissions;
    private List<AdminModulePermissionDTO> modulePermissions;
    /** true when ADMIN has no explicit grants (full access until owner restricts). */
    private Boolean legacyFullAccess;
}

