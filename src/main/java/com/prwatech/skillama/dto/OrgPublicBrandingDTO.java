package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.OrganizationBranding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgPublicBrandingDTO {
    private String organizationId;
    private String name;
    private String slug;
    private OrganizationBranding branding;
    private List<String> enabledFeatureCodes;
    private boolean requireSso;
}
