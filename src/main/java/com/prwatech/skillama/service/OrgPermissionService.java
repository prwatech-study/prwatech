package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.OrgAccessDTO;
import com.prwatech.skillama.dto.OrgAdminPermissionsDTO;
import com.prwatech.skillama.dto.OrgModulePermissionDTO;
import com.prwatech.skillama.dto.UpdateOrgPermissionsRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.OrgModule;
import com.prwatech.skillama.model.OrgModulePermission;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrgPermissionService {

    private final SkillamaUserRepository userRepository;

    /**
     * Stored on a newly created ORG_ADMIN so empty-list legacy full access does not apply.
     * Owner must then grant modules (same idea as Skillama owner → admin).
     */
    public static List<OrgModulePermission> noAccessUntilGranted() {
        return List.of(OrgModulePermission.builder()
                .module(OrgModule.USERS)
                .canRead(false)
                .canCreate(false)
                .canUpdate(false)
                .canDelete(false)
                .build());
    }

    public boolean usesLegacyFullAccess(User user) {
        if (user == null || user.getOrgRole() != OrgRole.ORG_ADMIN) {
            return false;
        }
        List<OrgModulePermission> grants = user.getOrgModulePermissions();
        return grants == null || grants.isEmpty();
    }

    public List<OrgModulePermissionDTO> resolveEffectivePermissions(User user) {
        if (user == null) {
            return List.of();
        }
        if (TenantSecurityService.isPlatformStaff(user) || user.getOrgRole() == OrgRole.ORG_OWNER) {
            return fullAccessDtoList();
        }
        if (user.getOrgRole() != OrgRole.ORG_ADMIN) {
            return List.of();
        }
        if (usesLegacyFullAccess(user)) {
            return fullAccessDtoList();
        }
        Map<OrgModule, OrgModulePermissionDTO> map = new EnumMap<>(OrgModule.class);
        for (OrgModule module : OrgModule.values()) {
            map.put(module, emptyPermissionDto(module));
        }
        for (OrgModulePermission grant : user.getOrgModulePermissions()) {
            if (grant == null || grant.getModule() == null) {
                continue;
            }
            map.put(grant.getModule(), toDto(grant));
        }
        return new ArrayList<>(map.values());
    }

    public OrgAccessDTO currentAccess(User user) {
        boolean operator = user != null
                && (user.getOrgRole() == OrgRole.ORG_OWNER || user.getOrgRole() == OrgRole.ORG_ADMIN
                || TenantSecurityService.isPlatformStaff(user));
        return OrgAccessDTO.builder()
                .hasAccess(operator)
                .orgRole(user != null && user.getOrgRole() != null ? user.getOrgRole().name() : null)
                .owner(user != null && user.getOrgRole() == OrgRole.ORG_OWNER)
                .legacyFullAccess(usesLegacyFullAccess(user))
                .modulePermissions(operator ? resolveEffectivePermissions(user) : List.of())
                .build();
    }

    public boolean hasPermission(User user, OrgModule module, AdminPermissionAction action) {
        if (user == null || module == null || action == null) {
            return false;
        }
        if (TenantSecurityService.isPlatformStaff(user) || user.getOrgRole() == OrgRole.ORG_OWNER) {
            return true;
        }
        if (user.getOrgRole() != OrgRole.ORG_ADMIN) {
            return false;
        }
        if (usesLegacyFullAccess(user)) {
            return true;
        }
        OrgModulePermission grant = findGrant(user, module);
        if (grant == null) {
            return false;
        }
        return switch (action) {
            case READ -> grant.isCanRead();
            case CREATE -> grant.isCanCreate();
            case UPDATE -> grant.isCanUpdate();
            case DELETE -> grant.isCanDelete();
        };
    }

    public void require(User user, OrgModule module, AdminPermissionAction action) {
        if (!hasPermission(user, module, action)) {
            throw new IllegalStateException(
                    "Insufficient permission for " + module.name() + " (" + action.name() + ")");
        }
    }

    public List<OrgAdminPermissionsDTO> listAdminsForOwner(User owner) {
        assertOwner(owner);
        return userRepository.findByOrganizationId(owner.getOrganizationId()).stream()
                .filter(u -> u.getOrgRole() == OrgRole.ORG_ADMIN)
                .map(this::toAdminDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrgAdminPermissionsDTO updateAdminPermissions(
            User owner, String targetUserId, UpdateOrgPermissionsRequestDTO body) {
        assertOwner(owner);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (owner.getOrganizationId() == null || !owner.getOrganizationId().equals(target.getOrganizationId())) {
            throw new IllegalStateException("User does not belong to this organization");
        }
        if (target.getOrgRole() != OrgRole.ORG_ADMIN) {
            throw new IllegalArgumentException("Permissions can only be assigned to organization admins");
        }
        List<OrgModulePermission> normalized = normalizeIncoming(body != null ? body.getPermissions() : null);
        if (normalized.isEmpty()) {
            normalized = new ArrayList<>(noAccessUntilGranted());
        }
        target.setOrgModulePermissions(normalized);
        target.setUpdatedAt(IndiaTime.now());
        target.setUpdatedBy(owner.getId());
        return toAdminDto(userRepository.save(target));
    }

    private void assertOwner(User actor) {
        if (actor == null) {
            throw new IllegalStateException("Only the organization owner can manage admin access");
        }
        if (TenantSecurityService.isPlatformStaff(actor)) {
            return;
        }
        if (actor.getOrgRole() != OrgRole.ORG_OWNER) {
            throw new IllegalStateException("Only the organization owner can manage admin access");
        }
    }

    private List<OrgModulePermission> normalizeIncoming(List<OrgModulePermissionDTO> incoming) {
        Map<OrgModule, OrgModulePermission> map = new EnumMap<>(OrgModule.class);
        if (incoming != null) {
            for (OrgModulePermissionDTO dto : incoming) {
                if (dto == null || !StringUtils.hasText(dto.getModule())) {
                    continue;
                }
                OrgModule module;
                try {
                    module = OrgModule.valueOf(dto.getModule().trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                boolean read = dto.isCanRead();
                boolean create = dto.isCanCreate();
                boolean update = dto.isCanUpdate();
                boolean delete = dto.isCanDelete();
                if (create || update || delete) {
                    read = true;
                }
                if (!read && !create && !update && !delete) {
                    continue;
                }
                map.put(module, OrgModulePermission.builder()
                        .module(module)
                        .canRead(read)
                        .canCreate(create)
                        .canUpdate(update)
                        .canDelete(delete)
                        .build());
            }
        }
        return new ArrayList<>(map.values());
    }

    private OrgModulePermission findGrant(User user, OrgModule module) {
        if (user.getOrgModulePermissions() == null) {
            return null;
        }
        return user.getOrgModulePermissions().stream()
                .filter(g -> g != null && g.getModule() == module)
                .findFirst()
                .orElse(null);
    }

    private OrgAdminPermissionsDTO toAdminDto(User user) {
        return OrgAdminPermissionsDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .legacyFullAccess(usesLegacyFullAccess(user))
                .permissions(resolveEffectivePermissions(user))
                .build();
    }

    private List<OrgModulePermissionDTO> fullAccessDtoList() {
        List<OrgModulePermissionDTO> list = new ArrayList<>();
        for (OrgModule module : OrgModule.values()) {
            list.add(OrgModulePermissionDTO.builder()
                    .module(module.name())
                    .canRead(true)
                    .canCreate(true)
                    .canUpdate(true)
                    .canDelete(true)
                    .build());
        }
        return list;
    }

    private OrgModulePermissionDTO emptyPermissionDto(OrgModule module) {
        return OrgModulePermissionDTO.builder()
                .module(module.name())
                .canRead(false)
                .canCreate(false)
                .canUpdate(false)
                .canDelete(false)
                .build();
    }

    private OrgModulePermissionDTO toDto(OrgModulePermission grant) {
        return OrgModulePermissionDTO.builder()
                .module(grant.getModule().name())
                .canRead(grant.isCanRead())
                .canCreate(grant.isCanCreate())
                .canUpdate(grant.isCanUpdate())
                .canDelete(grant.isCanDelete())
                .build();
    }
}
