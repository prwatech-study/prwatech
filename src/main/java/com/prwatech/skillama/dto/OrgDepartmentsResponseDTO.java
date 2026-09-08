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
public class OrgDepartmentsResponseDTO {
    @Builder.Default
    private List<OrgDepartmentDTO> departments = new ArrayList<>();

    @Builder.Default
    private List<OrgDepartmentCatalogItemDTO> catalog = new ArrayList<>();
}
