package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.OrgRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgUserDTO {
    private String userId;
    private String name;
    private String email;
    private OrgRole orgRole;
    private String managerUserId;
    private String managerName;
    private String department;
    private boolean active;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
