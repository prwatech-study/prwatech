package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.OrganizationSecurity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrgSecurityRequestDTO {
    private List<String> allowedEmailDomains;
    private Boolean requireSso;
    private Boolean jitProvisioning;
    private OrganizationSecurity.OrganizationPiiPolicy piiPolicy;
    /** SSO config keyed by feature code, e.g. sso_google_workspace → { hostedDomain } */
    private Map<String, Map<String, Object>> ssoConfig;
}
