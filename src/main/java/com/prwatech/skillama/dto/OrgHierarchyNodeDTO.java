package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.OrgRole;
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
public class OrgHierarchyNodeDTO {
    private String userId;
    private String name;
    private String email;
    private OrgRole orgRole;
    private String department;
    private String managerUserId;
    @Builder.Default
    private List<OrgHierarchyNodeDTO> reports = new ArrayList<>();
}
