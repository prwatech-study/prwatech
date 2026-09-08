package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportOrgUserRowDTO {
    private String name;
    private String email;
    /** LEARNER, MANAGER, or ORG_ADMIN */
    private String role;
    private String managerEmail;
    private String department;
}
