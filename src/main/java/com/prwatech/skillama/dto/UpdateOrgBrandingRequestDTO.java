package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.OrganizationBranding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrgBrandingRequestDTO {
    private OrganizationBranding branding;
}
