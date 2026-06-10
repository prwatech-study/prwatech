package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminModulePermissionDTO {
    private String module;
    private boolean canRead;
    private boolean canCreate;
    private boolean canUpdate;
    private boolean canDelete;
}
