package com.prwatech.skillama.model;

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
public class OrganizationSecurity {
    @Builder.Default
    private List<String> allowedEmailDomains = new ArrayList<>();
    @Builder.Default
    private boolean requireSso = false;
    @Builder.Default
    private boolean jitProvisioning = false;
    @Builder.Default
    private OrganizationPiiPolicy piiPolicy = new OrganizationPiiPolicy();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationPiiPolicy {
        @Builder.Default
        private boolean showPhoneToManagers = false;
    }
}
