package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organization_contract_renewals")
public class OrganizationContractRenewal {
    @Id
    private String id;

    @Indexed
    private String contractId;

    @Indexed
    private String organizationId;

    public enum RenewalEventType {
        INITIAL, RENEWAL, UPGRADE, DOWNGRADE, CANCELLED, REACTIVATED, EXPIRED
    }

    @Indexed
    private RenewalEventType eventType;

    private String packageCode;
    private int termMonths;
    private LocalDateTime previousPeriodStart;
    private LocalDateTime previousPeriodEnd;
    private LocalDateTime newPeriodStart;
    private LocalDateTime newPeriodEnd;

    @Indexed
    private LocalDateTime renewedAt;

    private String renewedBy;
    private String poNumber;
    private String notes;
}
