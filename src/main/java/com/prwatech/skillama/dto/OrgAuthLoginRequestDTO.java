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
public class OrgAuthLoginRequestDTO {
    private String orgSlug;
    private String email;
    private String password;
    private boolean forceLogin;
}
