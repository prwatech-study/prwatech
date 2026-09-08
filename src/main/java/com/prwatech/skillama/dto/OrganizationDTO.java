package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.OrganizationBranding;
import com.prwatech.skillama.model.OrganizationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDTO {
    private String id;
    private String name;
    private String slug;
    private String customDomain;
    private OrganizationStatus status;
    private String rootOwnerUserId;
    private String currentContractId;
    private String contactEmail;
    private String salesContactEmail;
    private OrganizationBranding branding;
    private List<String> allowedEmailDomains;
    private boolean requireSso;
    private boolean jitProvisioning;
    private LocalDateTime createdAt;
    private int activeUserCount;
    private int maxSeats;
}
