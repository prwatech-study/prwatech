package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organization_contracts")
@CompoundIndex(name = "contract_status_renewal", def = "{'status': 1, 'nextRenewalDate': 1}")
public class OrganizationContract {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    private String packageCode;
    private int termMonths;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime nextRenewalDate;
    private LocalDateTime lastRenewedAt;
    @Builder.Default
    private int renewalCount = 0;

    public enum ContractStatus {
        ACTIVE, EXPIRING_SOON, GRACE_PERIOD, EXPIRED, CANCELLED
    }

    @Indexed
    private ContractStatus status;

    @Builder.Default
    private boolean autoRenew = false;
    @Builder.Default
    private int gracePeriodDays = 7;

    private LocalDateTime expiring30SentAt;
    private LocalDateTime expiring7SentAt;
    private LocalDateTime graceStartedNotifiedAt;
    private LocalDateTime expiredNotifiedAt;

    private String poNumber;
    private String notes;
    @Builder.Default
    private long version = 0L;

    private LocalDateTime createdAt;
    private String createdBy;
}
