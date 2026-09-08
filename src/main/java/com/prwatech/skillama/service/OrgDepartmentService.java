package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CreateOrgDepartmentRequestDTO;
import com.prwatech.skillama.dto.OrgDepartmentCatalogItemDTO;
import com.prwatech.skillama.dto.OrgDepartmentDTO;
import com.prwatech.skillama.dto.OrgDepartmentsResponseDTO;
import com.prwatech.skillama.dto.UpdateOrgDepartmentRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.OrgModule;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationDepartment;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.OrganizationDepartmentRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgDepartmentService {

    private static final Pattern CODE = Pattern.compile("^[a-z0-9][a-z0-9-]{0,38}[a-z0-9]$");

    private final OrganizationDepartmentRepository departmentRepository;
    private final TenantSecurityService tenantSecurityService;
    private final OrgNotificationService orgNotificationService;
    private final OrgPermissionService orgPermissionService;

    public OrgDepartmentsResponseDTO list(User actor) {
        Organization org = requireOrg(actor);
        assertCanView(actor);
        List<OrganizationDepartment> rows =
                departmentRepository.findByOrganizationIdOrderByNameAsc(org.getId());
        Set<String> enabledCatalog = rows.stream()
                .map(OrganizationDepartment::getCatalogCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        List<OrgDepartmentCatalogItemDTO> catalog = OrgDepartmentCatalog.ITEMS.stream()
                .map(item -> OrgDepartmentCatalogItemDTO.builder()
                        .code(item.get("code"))
                        .name(item.get("name"))
                        .enabled(enabledCatalog.contains(item.get("code")))
                        .build())
                .toList();
        return OrgDepartmentsResponseDTO.builder()
                .departments(rows.stream().map(this::toDto).toList())
                .catalog(catalog)
                .build();
    }

    @Transactional
    public OrgDepartmentDTO createCustom(User actor, CreateOrgDepartmentRequestDTO request) {
        Organization org = requireOrg(actor);
        assertCanManageDepartments(actor, AdminPermissionAction.CREATE);
        String code = normalizeCode(request != null ? request.getCode() : null);
        String name = requireName(request != null ? request.getName() : null);
        if (departmentRepository.findByOrganizationIdAndCodeIgnoreCase(org.getId(), code).isPresent()) {
            throw new IllegalStateException("Department code already exists");
        }
        OrganizationDepartment row = OrganizationDepartment.builder()
                .organizationId(org.getId())
                .code(code)
                .name(name)
                .fromCatalog(false)
                .active(true)
                .createdAt(IndiaTime.now())
                .createdBy(actor.getId())
                .updatedAt(IndiaTime.now())
                .build();
        row = departmentRepository.save(row);
        orgNotificationService.notify(
                org.getId(),
                "ORG_DEPARTMENT_CREATED",
                "Department " + name + " (" + code + ") created",
                Map.of("departmentId", row.getId(), "code", code),
                actor);
        return toDto(row);
    }

    @Transactional
    public OrgDepartmentDTO enableCatalog(User actor, String catalogCode) {
        Organization org = requireOrg(actor);
        assertCanManageDepartments(actor, AdminPermissionAction.CREATE);
        Map<String, String> item = OrgDepartmentCatalog.find(catalogCode);
        if (item == null) {
            throw new IllegalArgumentException("Unknown catalog department");
        }
        return departmentRepository.findByOrganizationIdAndCatalogCode(org.getId(), item.get("code"))
                .map(existing -> {
                    if (!existing.isActive()) {
                        existing.setActive(true);
                        existing.setUpdatedAt(IndiaTime.now());
                        return toDto(departmentRepository.save(existing));
                    }
                    return toDto(existing);
                })
                .orElseGet(() -> {
                    if (departmentRepository.findByOrganizationIdAndCodeIgnoreCase(org.getId(), item.get("code"))
                            .isPresent()) {
                        throw new IllegalStateException("Department code already exists");
                    }
                    OrganizationDepartment row = OrganizationDepartment.builder()
                            .organizationId(org.getId())
                            .code(item.get("code"))
                            .name(item.get("name"))
                            .catalogCode(item.get("code"))
                            .fromCatalog(true)
                            .active(true)
                            .createdAt(IndiaTime.now())
                            .createdBy(actor.getId())
                            .updatedAt(IndiaTime.now())
                            .build();
                    return toDto(departmentRepository.save(row));
                });
    }

    @Transactional
    public OrgDepartmentDTO update(User actor, String departmentId, UpdateOrgDepartmentRequestDTO request) {
        Organization org = requireOrg(actor);
        assertCanManageDepartments(actor, AdminPermissionAction.UPDATE);
        OrganizationDepartment row = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        if (!org.getId().equals(row.getOrganizationId())) {
            throw new IllegalStateException("Department does not belong to this organization");
        }
        if (request != null && request.getName() != null && !request.getName().isBlank()) {
            row.setName(requireName(request.getName()));
        }
        if (request != null && request.getActive() != null) {
            row.setActive(request.getActive());
        }
        row.setUpdatedAt(IndiaTime.now());
        return toDto(departmentRepository.save(row));
    }

    /**
     * Maps a typed/CSV department to the org's department code when it matches
     * code or name. Unmatched values are kept as-is for legacy rows.
     */
    public String normalizeAssignment(String organizationId, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return departmentRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .filter(OrganizationDepartment::isActive)
                .filter(row -> trimmed.equalsIgnoreCase(row.getCode()) || trimmed.equalsIgnoreCase(row.getName()))
                .map(OrganizationDepartment::getCode)
                .findFirst()
                .orElse(trimmed);
    }

    private Organization requireOrg(User actor) {
        if (actor.getOrganizationId() == null) {
            throw new IllegalStateException("Organization context required");
        }
        return tenantSecurityService.requireActiveOrganization(actor.getOrganizationId());
    }

    private void assertCanView(User actor) {
        if (TenantSecurityService.isPlatformStaff(actor)) {
            return;
        }
        OrgRole role = actor.getOrgRole();
        if (role != OrgRole.ORG_OWNER && role != OrgRole.ORG_ADMIN) {
            throw new IllegalStateException("Insufficient organization permissions");
        }
        if (role == OrgRole.ORG_OWNER) {
            return;
        }
        if (orgPermissionService.hasPermission(actor, OrgModule.USERS, AdminPermissionAction.READ)
                || orgPermissionService.hasPermission(actor, OrgModule.DEPARTMENTS, AdminPermissionAction.READ)) {
            return;
        }
        throw new IllegalStateException("Insufficient permission for DEPARTMENTS (READ)");
    }

    private void assertCanManageDepartments(User actor, AdminPermissionAction action) {
        if (TenantSecurityService.isPlatformStaff(actor)) {
            return;
        }
        if (actor.getOrgRole() == OrgRole.ORG_OWNER) {
            return;
        }
        orgPermissionService.require(actor, OrgModule.DEPARTMENTS, action);
    }

    private String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Department code is required");
        }
        String code = raw.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Department code must be 2–40 characters: lowercase letters, numbers, and hyphens");
        }
        return code;
    }

    private String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Department name is required");
        }
        String name = raw.trim();
        if (name.length() > 80) {
            throw new IllegalArgumentException("Department name is too long");
        }
        return name;
    }

    private OrgDepartmentDTO toDto(OrganizationDepartment row) {
        return OrgDepartmentDTO.builder()
                .id(row.getId())
                .code(row.getCode())
                .name(row.getName())
                .catalogCode(row.getCatalogCode())
                .fromCatalog(row.isFromCatalog())
                .active(row.isActive())
                .build();
    }
}
