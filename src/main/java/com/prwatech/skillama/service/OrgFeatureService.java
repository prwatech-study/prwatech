package com.prwatech.skillama.service;

import com.prwatech.skillama.model.OrganizationContract;
import com.prwatech.skillama.model.OrganizationFeatureEntitlement;
import com.prwatech.skillama.model.PlatformFeature;
import com.prwatech.skillama.repository.OrganizationContractRepository;
import com.prwatech.skillama.repository.OrganizationFeatureEntitlementRepository;
import com.prwatech.skillama.repository.PlatformFeatureRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrgFeatureService {

    private final OrganizationFeatureEntitlementRepository entitlementRepository;
    private final OrganizationContractRepository contractRepository;
    private final PlatformFeatureRepository platformFeatureRepository;

    public boolean isEnabled(String organizationId, String featureCode) {
        if (organizationId == null || featureCode == null) {
            return false;
        }
        if (!isContractAllowsAccess(organizationId)) {
            return false;
        }
        OrganizationFeatureEntitlement entitlement = entitlementRepository
                .findByOrganizationIdAndFeatureCode(organizationId, featureCode)
                .orElse(null);
        if (entitlement == null || !entitlement.isEnabled()) {
            return false;
        }
        LocalDateTime now = IndiaTime.now();
        if (entitlement.getValidFrom() != null && now.isBefore(entitlement.getValidFrom())) {
            return false;
        }
        if (entitlement.getValidTo() != null && now.isAfter(entitlement.getValidTo())) {
            return false;
        }
        PlatformFeature feature = platformFeatureRepository.findByCode(featureCode).orElse(null);
        if (feature != null && feature.getDependsOn() != null) {
            for (String dep : feature.getDependsOn()) {
                if (!isEnabled(organizationId, dep)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Map<String, Object> getConfig(String organizationId, String featureCode) {
        return entitlementRepository.findByOrganizationIdAndFeatureCode(organizationId, featureCode)
                .map(OrganizationFeatureEntitlement::getConfigValue)
                .orElse(null);
    }

    public int getMaxSeats(String organizationId) {
        Map<String, Object> config = getConfig(organizationId, "max_seats");
        if (config == null || !config.containsKey("maxValue")) {
            return Integer.MAX_VALUE;
        }
        Object value = config.get("maxValue");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.MAX_VALUE;
    }

    public List<String> listEnabledFeatureCodes(String organizationId) {
        return entitlementRepository.findByOrganizationId(organizationId).stream()
                .map(OrganizationFeatureEntitlement::getFeatureCode)
                .filter(code -> isEnabled(organizationId, code))
                .sorted()
                .toList();
    }

    private boolean isContractAllowsAccess(String organizationId) {
        Optional<OrganizationContract> contractOpt = contractRepository.findByOrganizationId(organizationId);
        if (contractOpt.isEmpty()) {
            return true;
        }
        OrganizationContract.ContractStatus status = contractOpt.get().getStatus();
        return status == OrganizationContract.ContractStatus.ACTIVE
                || status == OrganizationContract.ContractStatus.EXPIRING_SOON
                || status == OrganizationContract.ContractStatus.GRACE_PERIOD;
    }
}
