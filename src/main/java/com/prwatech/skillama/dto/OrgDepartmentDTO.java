package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgDepartmentDTO {
    private String id;
    private String code;
    private String name;
    private String catalogCode;
    private boolean fromCatalog;
    private boolean active;
}
