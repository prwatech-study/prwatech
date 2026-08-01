package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AdminModulePermissionDTO;
import com.prwatech.skillama.dto.AdminUserPermissionsDTO;
import com.prwatech.skillama.dto.UpdateAdminPermissionsRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminModulePermission;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    private final SkillamaUserRepository userRepository;

    public boolean usesLegacyFullAccess(User user) {
        if (user == null || user.getRole() != User.UserRole.ADMIN) {
            return false;
        }
        List<AdminModulePermission> grants = user.getAdminModulePermissions();
        return grants == null || grants.isEmpty();
    }

    public List<AdminModulePermissionDTO> resolveEffectivePermissions(User user) {
        if (user == null) {
            return List.of();
        }
        if (user.getRole() == User.UserRole.OWNER) {
            return fullAccessDtoList();
        }
        if (user.getRole() == User.UserRole.TESTER) {
            Map<AdminModule, AdminModulePermissionDTO> testerMap = new EnumMap<>(AdminModule.class);
            for (AdminModule module : AdminModule.values()) {
                testerMap.put(module, emptyPermissionDto(module));
            }
            List<AdminModulePermission> grants = user.getAdminModulePermissions();
            if (grants != null) {
                for (AdminModulePermission grant : grants) {
                    if (grant == null || grant.getModule() == null
                            || !TESTER_ALLOWED_MODULES.contains(grant.getModule())) {
                        continue;
                    }
                    testerMap.put(grant.getModule(), toDto(grant));
                }
            }
            return new ArrayList<>(testerMap.values());
        }
        if (user.getRole() != User.UserRole.ADMIN) {
            return List.of();
        }
        if (usesLegacyFullAccess(user)) {
            return fullAccessDtoList();
        }
        Map<AdminModule, AdminModulePermissionDTO> map = new EnumMap<>(AdminModule.class);
        for (AdminModule module : AdminModule.values()) {
            map.put(module, emptyPermissionDto(module));
        }
        for (AdminModulePermission grant : user.getAdminModulePermissions()) {
            if (grant == null || grant.getModule() == null) {
                continue;
            }
            map.put(grant.getModule(), toDto(grant));
        }
        // Dashboard read always on for any admin with panel access
        AdminModulePermissionDTO dashboard = map.get(AdminModule.DASHBOARD);
        dashboard.setCanRead(true);
        return new ArrayList<>(map.values());
    }

    /** Modules a TESTER can ever be granted, regardless of what's stored on the user. */
    private static final java.util.Set<AdminModule> TESTER_ALLOWED_MODULES =
            java.util.EnumSet.of(AdminModule.COURSES, AdminModule.CURRICULUM);

    public boolean hasPermission(User user, AdminModule module, AdminPermissionAction action) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == User.UserRole.OWNER) {
            return true;
        }
        if (user.getRole() == User.UserRole.TESTER) {
            if (!TESTER_ALLOWED_MODULES.contains(module)) {
                return false;
            }
            AdminModulePermission grant = findGrant(user, module);
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
        if (user.getRole() != User.UserRole.ADMIN) {
            return false;
        }
        if (module == AdminModule.DASHBOARD && action == AdminPermissionAction.READ) {
            return true;
        }
        if (usesLegacyFullAccess(user)) {
            return true;
        }
        AdminModulePermission grant = findGrant(user, module);
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

    public void requirePermission(String userId, AdminModule module, AdminPermissionAction action) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != User.UserRole.ADMIN
                && user.getRole() != User.UserRole.OWNER
                && user.getRole() != User.UserRole.TESTER) {
            throw new RuntimeException("Admin access required");
        }
        if (!hasPermission(user, module, action)) {
            throw new RuntimeException(
                    "Insufficient permission for " + module.name() + " (" + action.name() + ")");
        }
    }

    public User requireAdminOrOwner(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != User.UserRole.ADMIN && user.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Admin access required");
        }
        return user;
    }

    public List<AdminUserPermissionsDTO> listAdminUsersForOwner() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.UserRole.ADMIN || u.getRole() == User.UserRole.TESTER)
                .map(this::toAdminUserPermissionsDto)
                .collect(Collectors.toList());
    }

    public AdminUserPermissionsDTO getAdminUserPermissions(String targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != User.UserRole.ADMIN && user.getRole() != User.UserRole.TESTER) {
            throw new IllegalArgumentException("Permissions can only be managed for ADMIN or TESTER users");
        }
        return toAdminUserPermissionsDto(user);
    }

    @Transactional
    public AdminUserPermissionsDTO updateAdminUserPermissions(
            String targetUserId,
            UpdateAdminPermissionsRequestDTO body,
            String ownerUserId) {
        requireOwner(ownerUserId);
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != User.UserRole.ADMIN && user.getRole() != User.UserRole.TESTER) {
            throw new IllegalArgumentException("Permissions can only be assigned to ADMIN or TESTER users");
        }
        List<AdminModulePermission> normalized = normalizeIncoming(body != null ? body.getPermissions() : null);
        if (user.getRole() == User.UserRole.TESTER) {
            normalized = normalized.stream()
                    .filter(p -> TESTER_ALLOWED_MODULES.contains(p.getModule()))
                    .collect(Collectors.toList());
        }
        user.setAdminModulePermissions(normalized);
        user.setUpdatedAt(IndiaTime.now());
        user.setUpdatedBy(ownerUserId);
        return toAdminUserPermissionsDto(userRepository.save(user));
    }

    public List<Map<String, String>> listAssignableModules() {
        return Arrays.stream(AdminModule.values())
                .map(m -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("module", m.name());
                    row.put("label", moduleLabel(m));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<AdminModulePermission> normalizeIncoming(List<AdminModulePermissionDTO> incoming) {
        Map<AdminModule, AdminModulePermission> map = new EnumMap<>(AdminModule.class);
        if (incoming != null) {
            for (AdminModulePermissionDTO dto : incoming) {
                if (dto == null || !StringUtils.hasText(dto.getModule())) {
                    continue;
                }
                AdminModule module;
                try {
                    module = AdminModule.valueOf(dto.getModule().trim().toUpperCase());
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
                map.put(module, AdminModulePermission.builder()
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

    private AdminModulePermission findGrant(User user, AdminModule module) {
        if (user.getAdminModulePermissions() == null) {
            return null;
        }
        return user.getAdminModulePermissions().stream()
                .filter(p -> p != null && module == p.getModule())
                .findFirst()
                .orElse(null);
    }

    private AdminUserPermissionsDTO toAdminUserPermissionsDto(User user) {
        return AdminUserPermissionsDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .legacyFullAccess(usesLegacyFullAccess(user))
                .permissions(resolveEffectivePermissions(user))
                .build();
    }

    private List<AdminModulePermissionDTO> fullAccessDtoList() {
        return Arrays.stream(AdminModule.values())
                .map(m -> AdminModulePermissionDTO.builder()
                        .module(m.name())
                        .canRead(true)
                        .canCreate(true)
                        .canUpdate(true)
                        .canDelete(true)
                        .build())
                .collect(Collectors.toList());
    }

    private AdminModulePermissionDTO emptyPermissionDto(AdminModule module) {
        return AdminModulePermissionDTO.builder()
                .module(module.name())
                .canRead(false)
                .canCreate(false)
                .canUpdate(false)
                .canDelete(false)
                .build();
    }

    private AdminModulePermissionDTO toDto(AdminModulePermission grant) {
        return AdminModulePermissionDTO.builder()
                .module(grant.getModule().name())
                .canRead(grant.isCanRead())
                .canCreate(grant.isCanCreate())
                .canUpdate(grant.isCanUpdate())
                .canDelete(grant.isCanDelete())
                .build();
    }

    public void requireOwner(String userId) {
        User user = requireAdminOrOwner(userId);
        if (user.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Owner access required");
        }
    }

    private String moduleLabel(AdminModule module) {
        return switch (module) {
            case DASHBOARD -> "Dashboard";
            case USERS -> "Users";
            case COURSES -> "Courses";
            case CURRICULUM -> "Curriculum";
            case ASSIGNMENTS -> "Assignments";
            case FEEDBACK -> "Feedback";
            case UPGRADE_REQUESTS -> "Upgrade requests";
            case AUDIT_LOGS -> "Audit logs";
            case ANALYTICS -> "Analytics";
            case FREEMIUM -> "Freemium";
            case SETTINGS_DEMO_VIDEO -> "Settings — Demo video";
            case SETTINGS_REFERRAL -> "Settings — Referral share";
            case SETTINGS_NOTIFICATIONS -> "Settings — Notifications";
            case SUPPORT -> "Support";
            case CHAT_MONITOR -> "AI chat monitor";
            case AI_USAGE -> "Money usage (AI usage)";
            case TESTER_EVALUATIONS -> "Tester evaluations";
            case AI_MENTOR_DOUBTS -> "AI Mentor doubts";
            case AI_EXAMS -> "AI Exam attempts";
            case MODULE_QUIZ_MONITOR -> "Module Quiz monitor";
        };
    }
}
