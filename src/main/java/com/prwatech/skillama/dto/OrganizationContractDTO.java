package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.OrganizationContract;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationContractDTO {
    private String id;
    private String organizationId;
    private String packageCode;
    private int termMonths;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime nextRenewalDate;
    private LocalDateTime lastRenewedAt;
    private int renewalCount;
    private OrganizationContract.ContractStatus status;
    private String poNumber;
}
