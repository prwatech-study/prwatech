package com.prwatech.skillama.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateAdminPermissionsRequestDTO {
    private List<AdminModulePermissionDTO> permissions;
}
