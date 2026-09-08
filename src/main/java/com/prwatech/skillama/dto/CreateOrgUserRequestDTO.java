package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.OrgRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrgUserRequestDTO {
    private String name;
    private String email;
    private String password;
    private OrgRole orgRole;
    private String managerUserId;
    private String department;
}
