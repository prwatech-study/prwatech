package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.*;
import com.prwatech.skillama.repository.*;
import com.prwatech.skillama.util.EmailValidation;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final Set<String> RESERVED_SLUGS = Set.of(
            "admin", "api", "www", "login", "team", "org", "platform", "skillama");

    /** The only feature codes the unauthenticated branding endpoint may disclose. */
    private static final List<String> PUBLIC_LOGIN_FEATURES = List.of(
            "email_password_auth", "sso_google_workspace", "sso_microsoft_entra");

    private static final Map<String, List<String>> PACKAGE_FEATURES = Map.of(
            "CORP_ENTERPRISE", List.of(
                    "ai_tutor", "ai_mentor", "code_lab", "debug_assistant", "ai_exam",
                    "module_quiz", "study_materials", "learner_analytics",
                    "team_analytics", "org_hierarchy", "org_user_management", "csv_user_import",
                    "white_label_branding", "email_password_auth", "max_seats"));

    private final OrganizationRepository organizationRepository;
    private final OrganizationContractRepository contractRepository;
    private final OrganizationContractRenewalRepository renewalRepository;
    private final OrganizationFeatureEntitlementRepository entitlementRepository;
    private final SkillamaUserRepository userRepository;
    private final PasswordEncode passwordEncode;
    private final OrgFeatureService orgFeatureService;
    private final OrgNotificationService orgNotificationService;
    private final AdminAuditService adminAuditService;
    private final OrgHierarchyService orgHierarchyService;

    public Page<OrganizationDTO> listOrganizations(int page, int size, OrganizationStatus status) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Organization> orgs = status != null
                ? organizationRepository.findByStatus(status, pageable)
                : organizationRepository.findAll(pageable);
        return orgs.map(this::toDto);
    }

    public List<OrganizationDTO> listExpiringOrganizations(int withinDays) {
        if (withinDays <= 0) {
            withinDays = 30;
        }
        LocalDateTime now = IndiaTime.now();
        LocalDateTime cutoff = now.plusDays(withinDays);
        return contractRepository.findAll().stream()
                .filter(c -> c.getCurrentPeriodEnd() != null
                        && !c.getCurrentPeriodEnd().isBefore(now)
                        && !c.getCurrentPeriodEnd().isAfter(cutoff))
                .map(c -> organizationRepository.findById(c.getOrganizationId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(this::toDto)
                .toList();
    }

    public OrganizationDTO getOrganization(String organizationId) {
        return toDto(requireOrg(organizationId));
    }

    public OrganizationContractDTO getContract(String organizationId) {
        OrganizationContract contract = contractRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
        return toContractDto(contract);
    }

    public OrgPublicBrandingDTO getPublicBranding(String slug) {
        Organization org = organizationRepository.findBySlug(slug.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        if (org.getStatus() == OrganizationStatus.ARCHIVED) {
            throw new ResourceNotFoundException("Organization not found");
        }
        return OrgPublicBrandingDTO.builder()
                .organizationId(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .branding(org.getBranding())
                .enabledFeatureCodes(publicLoginFeatureCodes(org.getId()))
                .requireSso(org.getSecurity() != null && org.getSecurity().isRequireSso())
                .build();
    }

    /**
     * Anonymous callers only need to know which sign-in methods to render. Returning the full
     * entitlement list here would publish each tenant's purchased packages to anyone who can
     * guess a slug.
     */
    private List<String> publicLoginFeatureCodes(String organizationId) {
        return PUBLIC_LOGIN_FEATURES.stream()
                .filter(code -> orgFeatureService.isEnabled(organizationId, code))
                .toList();
    }

    public OrgHostResolveDTO resolveHost(String host) {
        if (host == null || host.isBlank()) {
            throw new ResourceNotFoundException("Host required");
        }
        String normalized = host.trim().toLowerCase().split(":")[0];
        Optional<Organization> byCustom = organizationRepository.findByCustomDomain(normalized);
        if (byCustom.isPresent()) {
            Organization org = byCustom.get();
            return OrgHostResolveDTO.builder()
                    .organizationId(org.getId())
                    .slug(org.getSlug())
                    .name(org.getName())
                    .customDomain(true)
                    .build();
        }
        String corpSuffix = ".skillama.co.in";
        if (normalized.endsWith(corpSuffix)) {
            String sub = normalized.substring(0, normalized.length() - corpSuffix.length());
            if (!sub.isBlank() && !sub.contains(".")) {
                Organization org = organizationRepository.findBySlug(sub)
                        .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
                return OrgHostResolveDTO.builder()
                        .organizationId(org.getId())
                        .slug(org.getSlug())
                        .name(org.getName())
                        .customDomain(false)
                        .build();
            }
        }
        throw new ResourceNotFoundException("Organization not found for host");
    }

    @Transactional
    public OrganizationDTO updateSecurity(
            String organizationId, UpdateOrgSecurityRequestDTO request, User actor) {
        Organization org = requireOrg(organizationId);
        tenantAssertOrgActor(actor, organizationId);
        OrganizationSecurity security = org.getSecurity() != null
                ? org.getSecurity() : OrganizationSecurity.builder().build();
        if (request.getAllowedEmailDomains() != null) {
            security.setAllowedEmailDomains(normalizeDomains(request.getAllowedEmailDomains()));
        }
        if (request.getRequireSso() != null) {
            security.setRequireSso(request.getRequireSso());
        }
        if (request.getJitProvisioning() != null) {
            security.setJitProvisioning(request.getJitProvisioning());
        }
        if (request.getPiiPolicy() != null) {
            security.setPiiPolicy(request.getPiiPolicy());
        }
        org.setSecurity(security);
        org.setUpdatedAt(IndiaTime.now());
        org = organizationRepository.save(org);

        if (request.getSsoConfig() != null) {
            request.getSsoConfig().forEach((featureCode, config) ->
                    updateEntitlementConfig(organizationId, featureCode, config, actor.getId()));
        }

        orgNotificationService.notify(organizationId, "ORG_SECURITY_UPDATED", "Security settings updated", null, actor);
        return toDto(org);
    }

    private void updateEntitlementConfig(
            String organizationId, String featureCode, Map<String, Object> config, String actorId) {
        entitlementRepository.findByOrganizationIdAndFeatureCode(organizationId, featureCode)
                .ifPresent(ent -> {
                    ent.setConfigValue(config);
                    entitlementRepository.save(ent);
                });
    }

    public List<OrgHierarchyNodeDTO> getHierarchy(String organizationId) {
        requireOrg(organizationId);
        return orgHierarchyService.buildHierarchyTree(organizationId);
    }

    /** Owners/admins and platform staff see the whole org; a manager sees only their own subtree. */
    public List<OrgHierarchyNodeDTO> getHierarchyForActor(User actor) {
        requireOrg(actor.getOrganizationId());
        if (TenantSecurityService.isPlatformStaff(actor)
                || actor.getOrgRole() == OrgRole.ORG_OWNER
                || actor.getOrgRole() == OrgRole.ORG_ADMIN) {
            return orgHierarchyService.buildHierarchyTree(actor.getOrganizationId());
        }
        return orgHierarchyService.buildHierarchyTree(actor.getOrganizationId(), actor.getId());
    }

    @Transactional
    public OrganizationDTO createOrganization(CreateOrganizationRequestDTO request, String createdBy) {
        validateCreateRequest(request);
        String slug = request.getSlug().trim().toLowerCase();
        if (organizationRepository.existsBySlug(slug)) {
            throw new IllegalStateException("Organization slug already exists");
        }
        if (userRepository.findByEmail(request.getRootOwner().getEmail().trim()).isPresent()) {
            throw new IllegalStateException("Root owner email is already registered");
        }

        LocalDateTime now = IndiaTime.now();
        LocalDateTime periodStart = request.getContractStart() != null ? request.getContractStart() : now;
        LocalDateTime periodEnd = periodStart.plusMonths(request.getTermMonths());

        Organization org = Organization.builder()
                .name(request.getName().trim())
                .slug(slug)
                .status(OrganizationStatus.PROVISIONING)
                .contactEmail(request.getContactEmail().trim().toLowerCase())
                .salesContactEmail(request.getSalesContactEmail())
                .security(OrganizationSecurity.builder()
                        .allowedEmailDomains(normalizeDomains(request.getAllowedEmailDomains()))
                        .build())
                .createdAt(now)
                .createdBy(createdBy)
                .updatedAt(now)
                .build();
        org = organizationRepository.save(org);

        OrganizationContract contract = OrganizationContract.builder()
                .organizationId(org.getId())
                .packageCode(request.getPackageCode())
                .termMonths(request.getTermMonths())
                .currentPeriodStart(periodStart)
                .currentPeriodEnd(periodEnd)
                .nextRenewalDate(periodEnd)
                .renewalCount(0)
                .status(OrganizationContract.ContractStatus.ACTIVE)
                .poNumber(request.getPoNumber())
                .createdAt(now)
                .createdBy(createdBy)
                .build();
        contract = contractRepository.save(contract);

        org.setCurrentContractId(contract.getId());
        org = organizationRepository.save(org);

        renewalRepository.save(OrganizationContractRenewal.builder()
                .contractId(contract.getId())
                .organizationId(org.getId())
                .eventType(OrganizationContractRenewal.RenewalEventType.INITIAL)
                .packageCode(contract.getPackageCode())
                .termMonths(contract.getTermMonths())
                .newPeriodStart(periodStart)
                .newPeriodEnd(periodEnd)
                .renewedAt(now)
                .renewedBy(createdBy)
                .poNumber(request.getPoNumber())
                .build());

        seedEntitlements(org.getId(), request.getPackageCode(), periodStart, periodEnd, createdBy);

        User rootOwner = User.builder()
                .name(request.getRootOwner().getName().trim())
                .email(request.getRootOwner().getEmail().trim().toLowerCase())
                .password(passwordEncode.getEncryptedPassword(request.getRootOwner().getPassword()))
                .role(User.UserRole.USER)
                .orgRole(OrgRole.ORG_OWNER)
                .organizationId(org.getId())
                .planTier(User.PlanTier.ENTERPRISE)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();
        rootOwner = userRepository.save(rootOwner);

        org.setRootOwnerUserId(rootOwner.getId());
        org.setStatus(OrganizationStatus.ACTIVE);
        org = organizationRepository.save(org);

        User actor = userRepository.findById(createdBy).orElse(null);
        orgNotificationService.notify(org.getId(), "ORG_CREATED",
                "Organization " + org.getName() + " created", Map.of("slug", slug), actor);
        adminAuditService.log(createdBy, "ORG_CREATE", "ORGANIZATION", org.getId(),
                "Created organization " + org.getName(), null);

        return toDto(org);
    }

    @Transactional
    public OrganizationContractDTO renewContract(
            String organizationId, RenewOrganizationContractRequestDTO request, String renewedBy) {
        Organization org = requireOrg(organizationId);
        OrganizationContract contract = contractRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        LocalDateTime now = IndiaTime.now();
        LocalDateTime previousEnd = contract.getCurrentPeriodEnd();
        LocalDateTime newStart = previousEnd != null ? previousEnd.plusDays(1) : now;
        LocalDateTime newEnd = newStart.plusMonths(request.getTermMonths());

        renewalRepository.save(OrganizationContractRenewal.builder()
                .contractId(contract.getId())
                .organizationId(organizationId)
                .eventType(OrganizationContractRenewal.RenewalEventType.RENEWAL)
                .packageCode(request.getPackageCode() != null ? request.getPackageCode() : contract.getPackageCode())
                .termMonths(request.getTermMonths())
                .previousPeriodStart(contract.getCurrentPeriodStart())
                .previousPeriodEnd(previousEnd)
                .newPeriodStart(newStart)
                .newPeriodEnd(newEnd)
                .renewedAt(now)
                .renewedBy(renewedBy)
                .poNumber(request.getPoNumber())
                .notes(request.getNotes())
                .build());

        contract.setTermMonths(request.getTermMonths());
        if (request.getPackageCode() != null) {
            contract.setPackageCode(request.getPackageCode());
        }
        contract.setCurrentPeriodStart(newStart);
        contract.setCurrentPeriodEnd(newEnd);
        contract.setNextRenewalDate(newEnd);
        contract.setLastRenewedAt(now);
        contract.setRenewalCount(contract.getRenewalCount() + 1);
        contract.setStatus(OrganizationContract.ContractStatus.ACTIVE);
        contract.setExpiring30SentAt(null);
        contract.setExpiring7SentAt(null);
        contract.setGraceStartedNotifiedAt(null);
        contract.setExpiredNotifiedAt(null);
        contract.setVersion(contract.getVersion() + 1);
        contractRepository.save(contract);

        entitlementRepository.findByOrganizationId(organizationId).forEach(ent -> {
            ent.setValidTo(newEnd);
            entitlementRepository.save(ent);
        });

        org.setStatus(OrganizationStatus.ACTIVE);
        organizationRepository.save(org);

        User actor = userRepository.findById(renewedBy).orElse(null);
        orgNotificationService.notify(organizationId, "CONTRACT_RENEWED",
                "Contract renewed until " + newEnd.toLocalDate(), null, actor);

        return toContractDto(contract);
    }

    @Transactional
    public OrganizationDTO updateBranding(String organizationId, UpdateOrgBrandingRequestDTO request, User actor) {
        Organization org = requireOrg(organizationId);
        tenantAssertOrgActor(actor, organizationId);
        if (request.getBranding() != null) {
            org.setBranding(request.getBranding());
        }
        org.setUpdatedAt(IndiaTime.now());
        org = organizationRepository.save(org);
        orgNotificationService.notify(organizationId, "BRANDING_UPDATED", "Branding updated", null, actor);
        return toDto(org);
    }

    public Optional<Organization> findByEmailDomain(String email) {
        if (email == null || !email.contains("@")) {
            return Optional.empty();
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        return organizationRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrganizationStatus.ACTIVE
                        || o.getStatus() == OrganizationStatus.PROVISIONING)
                .filter(o -> o.getSecurity() != null
                        && o.getSecurity().getAllowedEmailDomains() != null
                        && o.getSecurity().getAllowedEmailDomains().stream()
                                .anyMatch(d -> d.equalsIgnoreCase(domain)))
                .findFirst();
    }

    public void assertB2cSignupAllowed(String email) {
        findByEmailDomain(email).ifPresent(org -> {
            throw new IllegalStateException(
                    "This email domain is reserved for " + org.getName()
                            + ". Sign in at /c/" + org.getSlug() + "/login or contact your administrator.");
        });
    }

    private void seedEntitlements(
            String organizationId,
            String packageCode,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            String grantedBy) {
        List<String> features = PACKAGE_FEATURES.getOrDefault(packageCode, List.of());
        LocalDateTime now = IndiaTime.now();
        for (String code : features) {
            Map<String, Object> config = null;
            if ("max_seats".equals(code)) {
                config = Map.of("maxValue", 50);
            }
            entitlementRepository.save(OrganizationFeatureEntitlement.builder()
                    .organizationId(organizationId)
                    .featureCode(code)
                    .enabled(true)
                    .configValue(config)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .grantedBy(grantedBy)
                    .grantedAt(now)
                    .build());
        }
    }

    private void validateCreateRequest(CreateOrganizationRequestDTO request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Organization name is required");
        }
        if (request.getSlug() == null || request.getSlug().isBlank()) {
            throw new IllegalArgumentException("Organization slug is required");
        }
        String slug = request.getSlug().trim().toLowerCase();
        if (RESERVED_SLUGS.contains(slug)) {
            throw new IllegalArgumentException("Slug is reserved");
        }
        if (request.getContactEmail() == null || request.getContactEmail().isBlank()) {
            throw new IllegalArgumentException("Contact email is required");
        }
        EmailValidation.assertValidFormat(request.getContactEmail());
        if (request.getRootOwner() == null
                || request.getRootOwner().getEmail() == null
                || request.getRootOwner().getPassword() == null) {
            throw new IllegalArgumentException("Root owner name, email, and password are required");
        }
        if (request.getTermMonths() <= 0) {
            throw new IllegalArgumentException("termMonths must be positive");
        }
        if (request.getPackageCode() == null || request.getPackageCode().isBlank()) {
            request.setPackageCode("CORP_ENTERPRISE");
        }
    }

    private List<String> normalizeDomains(List<String> domains) {
        if (domains == null) {
            return List.of();
        }
        return domains.stream()
                .filter(d -> d != null && !d.isBlank())
                .map(d -> d.trim().toLowerCase().replaceFirst("^@", ""))
                .distinct()
                .toList();
    }

    private Organization requireOrg(String organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    private void tenantAssertOrgActor(User actor, String organizationId) {
        if (TenantSecurityService.isPlatformStaff(actor)) {
            return;
        }
        if (actor.getOrganizationId() == null || !actor.getOrganizationId().equals(organizationId)) {
            throw new IllegalStateException("Organization access denied");
        }
        OrgRole role = actor.getOrgRole();
        if (role != OrgRole.ORG_OWNER && role != OrgRole.ORG_ADMIN) {
            throw new IllegalStateException("Insufficient organization permissions");
        }
    }

    private OrganizationDTO toDto(Organization org) {
        int activeUsers = (int) userRepository.countByOrganizationIdAndActiveTrue(org.getId());
        return OrganizationDTO.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .customDomain(org.getCustomDomain())
                .status(org.getStatus())
                .rootOwnerUserId(org.getRootOwnerUserId())
                .currentContractId(org.getCurrentContractId())
                .contactEmail(org.getContactEmail())
                .salesContactEmail(org.getSalesContactEmail())
                .branding(org.getBranding())
                .allowedEmailDomains(org.getSecurity() != null
                        ? org.getSecurity().getAllowedEmailDomains() : List.of())
                .requireSso(org.getSecurity() != null && org.getSecurity().isRequireSso())
                .jitProvisioning(org.getSecurity() != null && org.getSecurity().isJitProvisioning())
                .createdAt(org.getCreatedAt())
                .activeUserCount(activeUsers)
                .maxSeats(orgFeatureService.getMaxSeats(org.getId()))
                .build();
    }

    private OrganizationContractDTO toContractDto(OrganizationContract contract) {
        return OrganizationContractDTO.builder()
                .id(contract.getId())
                .organizationId(contract.getOrganizationId())
                .packageCode(contract.getPackageCode())
                .termMonths(contract.getTermMonths())
                .currentPeriodStart(contract.getCurrentPeriodStart())
                .currentPeriodEnd(contract.getCurrentPeriodEnd())
                .nextRenewalDate(contract.getNextRenewalDate())
                .lastRenewedAt(contract.getLastRenewedAt())
                .renewalCount(contract.getRenewalCount())
                .status(contract.getStatus())
                .poNumber(contract.getPoNumber())
                .build();
    }
}
