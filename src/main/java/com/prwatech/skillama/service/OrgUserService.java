package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.BulkImportOrgUsersRequestDTO;
import com.prwatech.skillama.dto.BulkImportOrgUsersResultDTO;
import com.prwatech.skillama.dto.CreateOrgUserRequestDTO;
import com.prwatech.skillama.dto.ImportOrgUserRowDTO;
import com.prwatech.skillama.dto.OrgUserDTO;
import com.prwatech.skillama.dto.UpdateOrgUserRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.OrgModule;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.EmailValidation;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgUserService {

    private final SkillamaUserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncode passwordEncode;
    private final TenantSecurityService tenantSecurityService;
    private final OrgHierarchyService orgHierarchyService;
    private final OrgFeatureService orgFeatureService;
    private final OrgNotificationService orgNotificationService;
    private final OrgDepartmentService orgDepartmentService;
    private final OrgPermissionService orgPermissionService;

    public List<OrgUserDTO> listUsers(User actor) {
        Organization org = requireActorOrg(actor);
        assertCanListUsers(actor);
        List<User> users = userRepository.findByOrganizationId(org.getId());
        Map<String, User> managers = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        return users.stream()
                .sorted(Comparator.comparing(User::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(user -> toDto(user, managers))
                .toList();
    }

    @Transactional
    public OrgUserDTO createUser(User actor, CreateOrgUserRequestDTO request) {
        Organization org = requireActorOrg(actor);
        assertCanManageUsers(actor, AdminPermissionAction.CREATE);
        return createUserInOrg(org, request, actor);
    }

    @Transactional
    public OrgUserDTO createUserForOrganization(
            String organizationId, CreateOrgUserRequestDTO request, User platformActor) {
        Organization org = tenantSecurityService.requireActiveOrganization(organizationId);
        return createUserInOrg(org, request, platformActor);
    }

    @Transactional
    public OrgUserDTO updateUser(User actor, String userId, UpdateOrgUserRequestDTO request) {
        Organization org = requireActorOrg(actor);
        assertCanManageUsers(actor, AdminPermissionAction.UPDATE);
        User target = requireOrgUser(org.getId(), userId);
        return applyUpdate(org, actor, target, request);
    }

    @Transactional
    public OrgUserDTO updateUserForOrganization(
            String organizationId, String userId, UpdateOrgUserRequestDTO request, User platformActor) {
        Organization org = tenantSecurityService.requireActiveOrganization(organizationId);
        User target = requireOrgUser(org.getId(), userId);
        return applyUpdate(org, platformActor, target, request);
    }

    @Transactional
    public OrgUserDTO transferOwnership(User actor, String newOwnerUserId) {
        Organization org = requireActorOrg(actor);
        if (actor.getOrgRole() != OrgRole.ORG_OWNER && !TenantSecurityService.isPlatformStaff(actor)) {
            throw new IllegalStateException("Only the organization owner can transfer ownership");
        }
        User newOwner = requireOrgUser(org.getId(), newOwnerUserId);
        if (newOwner.getOrgRole() == OrgRole.ORG_OWNER) {
            return toDto(newOwner, managerMap(org.getId()));
        }

        User previousOwner = userRepository.findById(org.getRootOwnerUserId()).orElse(null);
        if (previousOwner != null && !previousOwner.getId().equals(newOwner.getId())) {
            previousOwner.setOrgRole(OrgRole.ORG_ADMIN);
            previousOwner.setUpdatedAt(IndiaTime.now());
            userRepository.save(previousOwner);
        }

        newOwner.setOrgRole(OrgRole.ORG_OWNER);
        newOwner.setUpdatedAt(IndiaTime.now());
        newOwner = userRepository.save(newOwner);

        org.setRootOwnerUserId(newOwner.getId());
        org.setUpdatedAt(IndiaTime.now());
        organizationRepository.save(org);

        orgNotificationService.notify(
                org.getId(),
                "ORG_OWNERSHIP_TRANSFERRED",
                "Organization ownership transferred to " + newOwner.getEmail(),
                null,
                actor);
        return toDto(newOwner, managerMap(org.getId()));
    }

    @Transactional
    public BulkImportOrgUsersResultDTO bulkImportUsers(User actor, BulkImportOrgUsersRequestDTO request) {
        Organization org = requireActorOrg(actor);
        assertCanManageUsers(actor, AdminPermissionAction.CREATE);
        if (!orgFeatureService.isEnabled(org.getId(), "csv_user_import")) {
            throw new IllegalStateException("CSV import is not enabled for this organization");
        }
        if (request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw new IllegalArgumentException("At least one row is required");
        }
        if (request.getDefaultPassword() == null || request.getDefaultPassword().isBlank()) {
            throw new IllegalArgumentException("Default password is required for imported users");
        }
        EmailValidation.assertPasswordLength(request.getDefaultPassword());

        List<BulkImportOrgUsersResultDTO.ImportOrgUserFailureDTO> failures = new ArrayList<>();
        Map<String, User> emailToUser = userRepository.findByOrganizationId(org.getId()).stream()
                .collect(Collectors.toMap(
                        u -> u.getEmail().toLowerCase(),
                        u -> u,
                        (a, b) -> a,
                        HashMap::new));

        int createdCount = 0;
        for (int i = 0; i < request.getRows().size(); i++) {
            ImportOrgUserRowDTO row = request.getRows().get(i);
            String email = row.getEmail() != null ? row.getEmail().trim().toLowerCase() : "";
            try {
                if (row.getName() == null || row.getName().isBlank()) {
                    throw new IllegalArgumentException("Name is required");
                }
                if (email.isBlank()) {
                    throw new IllegalArgumentException("Email is required");
                }
                EmailValidation.assertValidFormat(email);
                assertEmailAllowed(org, email);

                if (userRepository.findByEmail(email).isPresent()) {
                    if (!emailToUser.containsKey(email)) {
                        throw new IllegalStateException("Email is already registered outside this organization");
                    }
                    continue;
                }

                OrgRole role = parseImportRole(row.getRole());
                CreateOrgUserRequestDTO create = CreateOrgUserRequestDTO.builder()
                        .name(row.getName().trim())
                        .email(email)
                        .password(request.getDefaultPassword())
                        .orgRole(role)
                        .department(blankToNull(row.getDepartment()))
                        .build();
                OrgUserDTO created = createUserInOrg(org, create, actor);
                createdCount++;
                emailToUser.put(email, userRepository.findById(created.getUserId()).orElseThrow());
            } catch (Exception e) {
                failures.add(BulkImportOrgUsersResultDTO.ImportOrgUserFailureDTO.builder()
                        .rowIndex(i + 1)
                        .email(email.isBlank() ? null : email)
                        .message(e.getMessage())
                        .build());
            }
        }

        int managersLinked = 0;
        for (int i = 0; i < request.getRows().size(); i++) {
            ImportOrgUserRowDTO row = request.getRows().get(i);
            if (row.getManagerEmail() == null || row.getManagerEmail().isBlank()) {
                continue;
            }
            String email = row.getEmail() != null ? row.getEmail().trim().toLowerCase() : "";
            String managerEmail = row.getManagerEmail().trim().toLowerCase();
            User user = emailToUser.get(email);
            User manager = emailToUser.get(managerEmail);
            if (user == null || manager == null) {
                failures.add(BulkImportOrgUsersResultDTO.ImportOrgUserFailureDTO.builder()
                        .rowIndex(i + 1)
                        .email(email.isBlank() ? null : email)
                        .message("Could not link manager: user or manager not found in organization")
                        .build());
                continue;
            }
            if (user.getId().equals(manager.getId())) {
                continue;
            }
            try {
                orgHierarchyService.validateManagerAssignment(org.getId(), user.getId(), manager.getId());
                user.setManagerUserId(manager.getId());
                user.setUpdatedAt(IndiaTime.now());
                user.setUpdatedBy(actor.getId());
                userRepository.save(user);
                managersLinked++;
            } catch (Exception e) {
                failures.add(BulkImportOrgUsersResultDTO.ImportOrgUserFailureDTO.builder()
                        .rowIndex(i + 1)
                        .email(email)
                        .message("Manager link failed: " + e.getMessage())
                        .build());
            }
        }

        return BulkImportOrgUsersResultDTO.builder()
                .createdCount(createdCount)
                .managersLinkedCount(managersLinked)
                .failures(failures)
                .build();
    }

    private OrgRole parseImportRole(String role) {
        if (role == null || role.isBlank()) {
            return OrgRole.LEARNER;
        }
        try {
            OrgRole parsed = OrgRole.valueOf(role.trim().toUpperCase());
            if (parsed == OrgRole.ORG_OWNER) {
                throw new IllegalArgumentException("Cannot import ORG_OWNER via CSV");
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }
    }

    private OrgUserDTO createUserInOrg(Organization org, CreateOrgUserRequestDTO request, User actor) {
        validateCreateRequest(org, request);
        assertSeatAvailable(org.getId());

        if (userRepository.findByEmail(request.getEmail().trim().toLowerCase()).isPresent()) {
            throw new IllegalStateException("Email is already registered");
        }

        OrgRole role = request.getOrgRole() != null ? request.getOrgRole() : OrgRole.LEARNER;
        if (role == OrgRole.ORG_OWNER) {
            throw new IllegalArgumentException("Create ORG_OWNER only during organization provisioning");
        }
        orgHierarchyService.validateManagerAssignment(org.getId(), null, request.getManagerUserId());

        LocalDateTime now = IndiaTime.now();
        User user = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncode.getEncryptedPassword(request.getPassword()))
                .role(User.UserRole.USER)
                .orgRole(role)
                .organizationId(org.getId())
                .managerUserId(blankToNull(request.getManagerUserId()))
                .department(orgDepartmentService.normalizeAssignment(org.getId(), request.getDepartment()))
                .orgModulePermissions(role == OrgRole.ORG_ADMIN
                        ? new ArrayList<>(OrgPermissionService.noAccessUntilGranted())
                        : new ArrayList<>())
                .planTier(User.PlanTier.ENTERPRISE)
                .active(true)
                .emailVerified(true)
                .authProvider(User.AuthProvider.EMAIL)
                .onboardingCompleted(true)
                .onboardingCompletedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(actor != null ? actor.getId() : "SYSTEM")
                .updatedBy(actor != null ? actor.getId() : "SYSTEM")
                .build();
        user = userRepository.save(user);

        orgNotificationService.notify(
                org.getId(),
                "ORG_USER_CREATED",
                "User " + user.getEmail() + " added to organization",
                Map.of("userId", user.getId(), "orgRole", role.name()),
                actor);
        return toDto(user, managerMap(org.getId()));
    }

    private OrgUserDTO applyUpdate(
            Organization org, User actor, User target, UpdateOrgUserRequestDTO request) {
        if (target.getOrgRole() == OrgRole.ORG_OWNER
                && actor != null
                && actor.getOrgRole() == OrgRole.ORG_ADMIN
                && !TenantSecurityService.isPlatformStaff(actor)) {
            throw new IllegalStateException("Organization admins cannot edit the owner");
        }
        if (target.getOrgRole() == OrgRole.ORG_OWNER && request.getOrgRole() != null
                && request.getOrgRole() != OrgRole.ORG_OWNER) {
            throw new IllegalStateException("Transfer ownership before demoting the organization owner");
        }
        if (request.getOrgRole() == OrgRole.ORG_OWNER) {
            throw new IllegalArgumentException("Use ownership transfer to assign ORG_OWNER");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            target.setName(request.getName().trim());
        }
        if (request.getDepartment() != null) {
            target.setDepartment(orgDepartmentService.normalizeAssignment(org.getId(), request.getDepartment()));
        }
        if (request.getManagerUserId() != null) {
            orgHierarchyService.validateManagerAssignment(
                    org.getId(), target.getId(), blankToNull(request.getManagerUserId()));
            target.setManagerUserId(blankToNull(request.getManagerUserId()));
        }
        if (request.getOrgRole() != null) {
            OrgRole previous = target.getOrgRole();
            target.setOrgRole(request.getOrgRole());
            if (request.getOrgRole() == OrgRole.ORG_ADMIN && previous != OrgRole.ORG_ADMIN) {
                target.setOrgModulePermissions(new ArrayList<>(OrgPermissionService.noAccessUntilGranted()));
            }
            if (request.getOrgRole() != OrgRole.ORG_ADMIN) {
                target.setOrgModulePermissions(new ArrayList<>());
            }
        }
        if (request.getActive() != null) {
            if (target.getOrgRole() == OrgRole.ORG_OWNER && !request.getActive()) {
                throw new IllegalStateException("Cannot deactivate the organization owner");
            }
            if (request.getActive() && !target.isActive()) {
                assertSeatAvailable(org.getId());
            }
            target.setActive(request.getActive());
        }
        target.setUpdatedAt(IndiaTime.now());
        target.setUpdatedBy(actor != null ? actor.getId() : "SYSTEM");
        target = userRepository.save(target);

        orgNotificationService.notify(
                org.getId(),
                "ORG_USER_UPDATED",
                "User " + target.getEmail() + " updated",
                Map.of("userId", target.getId()),
                actor);
        return toDto(target, managerMap(org.getId()));
    }

    private void validateCreateRequest(Organization org, CreateOrgUserRequestDTO request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        EmailValidation.assertValidFormat(request.getEmail());
        EmailValidation.assertPasswordLength(request.getPassword());
        assertEmailAllowed(org, request.getEmail());
    }

    private void assertEmailAllowed(Organization org, String email) {
        if (org.getSecurity() == null
                || org.getSecurity().getAllowedEmailDomains() == null
                || org.getSecurity().getAllowedEmailDomains().isEmpty()) {
            return;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        boolean allowed = org.getSecurity().getAllowedEmailDomains().stream()
                .anyMatch(d -> d.equalsIgnoreCase(domain));
        if (!allowed) {
            throw new IllegalArgumentException("Email domain is not allowed for this organization");
        }
    }

    private void assertSeatAvailable(String organizationId) {
        int active = (int) userRepository.countByOrganizationIdAndActiveTrue(organizationId);
        int maxSeats = orgFeatureService.getMaxSeats(organizationId);
        if (active >= maxSeats) {
            throw new IllegalStateException("Organization seat limit reached (" + maxSeats + ")");
        }
    }

    private void assertCanListUsers(User actor) {
        if (TenantSecurityService.isPlatformStaff(actor)) {
            return;
        }
        OrgRole role = actor.getOrgRole();
        if (role != OrgRole.ORG_OWNER && role != OrgRole.ORG_ADMIN) {
            throw new IllegalStateException("Insufficient organization permissions");
        }
        boolean canReadUsers = orgPermissionService.hasPermission(actor, OrgModule.USERS, AdminPermissionAction.READ);
        boolean canReadAssignments = orgPermissionService.hasPermission(
                actor, OrgModule.ASSIGNMENTS, AdminPermissionAction.READ);
        if (!canReadUsers && !canReadAssignments) {
            orgPermissionService.require(actor, OrgModule.USERS, AdminPermissionAction.READ);
        }
        if (!orgFeatureService.isEnabled(actor.getOrganizationId(), "org_user_management")) {
            throw new IllegalStateException("User management is not enabled for this organization");
        }
    }

    private void assertCanManageUsers(User actor, AdminPermissionAction action) {
        if (TenantSecurityService.isPlatformStaff(actor)) {
            return;
        }
        OrgRole role = actor.getOrgRole();
        if (role != OrgRole.ORG_OWNER && role != OrgRole.ORG_ADMIN) {
            throw new IllegalStateException("Insufficient organization permissions");
        }
        orgPermissionService.require(actor, OrgModule.USERS, action);
        if (!orgFeatureService.isEnabled(actor.getOrganizationId(), "org_user_management")) {
            throw new IllegalStateException("User management is not enabled for this organization");
        }
    }

    private Organization requireActorOrg(User actor) {
        if (actor.getOrganizationId() == null) {
            throw new IllegalStateException("Organization context required");
        }
        return tenantSecurityService.requireActiveOrganization(actor.getOrganizationId());
    }

    private User requireOrgUser(String organizationId, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        tenantSecurityService.assertUserInOrg(user, organizationId);
        return user;
    }

    private Map<String, User> managerMap(String organizationId) {
        return userRepository.findByOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private OrgUserDTO toDto(User user, Map<String, User> managers) {
        String managerName = null;
        if (user.getManagerUserId() != null && managers.containsKey(user.getManagerUserId())) {
            managerName = managers.get(user.getManagerUserId()).getName();
        }
        return OrgUserDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .orgRole(user.getOrgRole() != null ? user.getOrgRole() : OrgRole.LEARNER)
                .managerUserId(user.getManagerUserId())
                .managerName(managerName)
                .department(user.getDepartment())
                .active(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
