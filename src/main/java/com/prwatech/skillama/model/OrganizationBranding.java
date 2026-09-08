package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationBranding {
    private String logoUrl;
    private String faviconUrl;
    private String primaryColor;
    private String accentColor;
    private String loginWelcomeText;
    private String loginBackgroundUrl;
    /** classic | aurora */
    private String defaultLmsTheme;
    private String certificateLogoUrl;
    private String emailFooterText;
}
