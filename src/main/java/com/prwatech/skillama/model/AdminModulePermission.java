package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminModulePermission {
    private AdminModule module;
    private boolean canRead;
    private boolean canCreate;
    private boolean canUpdate;
    private boolean canDelete;
}
