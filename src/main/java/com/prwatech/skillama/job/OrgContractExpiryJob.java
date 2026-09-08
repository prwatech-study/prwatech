package com.prwatech.skillama.job;

import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationContract;
import com.prwatech.skillama.model.OrganizationStatus;
import com.prwatech.skillama.repository.OrganizationContractRepository;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.service.OrgNotificationService;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrgContractExpiryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrgContractExpiryJob.class);

    private final OrganizationContractRepository contractRepository;
    private final OrganizationRepository organizationRepository;
    private final OrgNotificationService orgNotificationService;

    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Kolkata")
    public void processContractExpiryAlerts() {
        LocalDateTime now = IndiaTime.now();
        List<OrganizationContract> contracts = contractRepository.findByStatusIn(
                EnumSet.of(
                        OrganizationContract.ContractStatus.ACTIVE,
                        OrganizationContract.ContractStatus.EXPIRING_SOON,
                        OrganizationContract.ContractStatus.GRACE_PERIOD));

        for (OrganizationContract contract : contracts) {
            try {
                processContract(contract, now);
            } catch (Exception e) {
                LOGGER.warn("Contract expiry job failed for contract {}: {}", contract.getId(), e.getMessage());
            }
        }
    }

    private void processContract(OrganizationContract contract, LocalDateTime now) {
        if (contract.getCurrentPeriodEnd() == null) {
            return;
        }
        Organization org = organizationRepository.findById(contract.getOrganizationId()).orElse(null);
        if (org == null) {
            return;
        }

        LocalDateTime periodEnd = contract.getCurrentPeriodEnd();
        int graceDays = contract.getGracePeriodDays() > 0 ? contract.getGracePeriodDays() : 7;
        LocalDateTime graceEnds = periodEnd.plusDays(graceDays);

        if (!now.isAfter(periodEnd)) {
            long daysUntilEnd = java.time.Duration.between(now, periodEnd).toDays();
            if (daysUntilEnd <= 30 && contract.getExpiring30SentAt() == null) {
                contract.setStatus(OrganizationContract.ContractStatus.EXPIRING_SOON);
                contract.setExpiring30SentAt(now);
                contractRepository.save(contract);
                orgNotificationService.notify(
                        org.getId(),
                        "ORG_CONTRACT_EXPIRING_30",
                        "Contract expires in 30 days (" + periodEnd.toLocalDate() + ")",
                        null,
                        null);
            } else if (daysUntilEnd <= 7 && contract.getExpiring7SentAt() == null) {
                contract.setExpiring7SentAt(now);
                contractRepository.save(contract);
                orgNotificationService.notify(
                        org.getId(),
                        "ORG_CONTRACT_EXPIRING_7",
                        "Contract expires in 7 days (" + periodEnd.toLocalDate() + ")",
                        null,
                        null);
            }
            return;
        }

        if (!now.isAfter(graceEnds)) {
            if (contract.getStatus() != OrganizationContract.ContractStatus.GRACE_PERIOD) {
                contract.setStatus(OrganizationContract.ContractStatus.GRACE_PERIOD);
                contractRepository.save(contract);
            }
            if (contract.getGraceStartedNotifiedAt() == null) {
                contract.setGraceStartedNotifiedAt(now);
                contractRepository.save(contract);
                orgNotificationService.notify(
                        org.getId(),
                        "ORG_CONTRACT_GRACE_PERIOD",
                        "Contract expired; grace period ends " + graceEnds.toLocalDate(),
                        null,
                        null);
            }
            return;
        }

        if (contract.getStatus() != OrganizationContract.ContractStatus.EXPIRED) {
            contract.setStatus(OrganizationContract.ContractStatus.EXPIRED);
            contractRepository.save(contract);
        }
        if (org.getStatus() != OrganizationStatus.SUSPENDED) {
            org.setStatus(OrganizationStatus.SUSPENDED);
            organizationRepository.save(org);
        }
        if (contract.getExpiredNotifiedAt() == null) {
            contract.setExpiredNotifiedAt(now);
            contractRepository.save(contract);
            orgNotificationService.notify(
                    org.getId(),
                    "ORG_CONTRACT_EXPIRED",
                    "Contract expired; organization access suspended",
                    null,
                    null);
        }
    }
}
