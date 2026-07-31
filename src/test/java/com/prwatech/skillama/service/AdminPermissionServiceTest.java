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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPermissionServiceTest {

    @Mock private SkillamaUserRepository userRepository;

    private AdminPermissionService service;

    @BeforeEach
    void setUp() {
        service = new AdminPermissionService(userRepository);
    }

    private User owner() {
        return User.builder().id("owner").role(User.UserRole.OWNER).build();
    }

    private User learner() {
        return User.builder().id("learner").role(User.UserRole.USER).build();
    }

    private User adminWithGrants(AdminModulePermission... grants) {
        return User.builder().id("admin").role(User.UserRole.ADMIN)
                .adminModulePermissions(new ArrayList<>(List.of(grants))).build();
    }

    private User legacyAdmin() {
        return User.builder().id("admin").role(User.UserRole.ADMIN)
                .adminModulePermissions(new ArrayList<>()).build();
    }

    private User testerWithGrants(AdminModulePermission... grants) {
        return User.builder().id("tester").role(User.UserRole.TESTER)
                .adminModulePermissions(new ArrayList<>(List.of(grants))).build();
    }

    // ---------- usesLegacyFullAccess ----------

    @Test
    void legacyAccessOnlyForAdminWithNoGrants() {
        assertTrue(service.usesLegacyFullAccess(legacyAdmin()));
        assertFalse(service.usesLegacyFullAccess(owner()));
        assertFalse(service.usesLegacyFullAccess(learner()));
        assertFalse(service.usesLegacyFullAccess(adminWithGrants(
                AdminModulePermission.builder().module(AdminModule.USERS).canRead(true).build())));
    }

    // ---------- hasPermission ----------

    @Test
    void ownerHasEveryPermission() {
        for (AdminModule m : AdminModule.values()) {
            for (AdminPermissionAction a : AdminPermissionAction.values()) {
                assertTrue(service.hasPermission(owner(), m, a));
            }
        }
    }

    @Test
    void nullOrLearnerHasNoPermission() {
        assertFalse(service.hasPermission(null, AdminModule.USERS, AdminPermissionAction.READ));
        assertFalse(service.hasPermission(learner(), AdminModule.USERS, AdminPermissionAction.READ));
    }

    @Test
    void dashboardReadAlwaysAllowedForAdmin() {
        User admin = adminWithGrants(
                AdminModulePermission.builder().module(AdminModule.USERS).canRead(true).build());
        assertTrue(service.hasPermission(admin, AdminModule.DASHBOARD, AdminPermissionAction.READ));
    }

    @Test
    void legacyAdminHasFullPermission() {
        assertTrue(service.hasPermission(legacyAdmin(), AdminModule.COURSES, AdminPermissionAction.DELETE));
    }

    @Test
    void grantScopedAdminOnlyHasGrantedActions() {
        User admin = adminWithGrants(AdminModulePermission.builder()
                .module(AdminModule.COURSES).canRead(true).canUpdate(true).build());

        assertTrue(service.hasPermission(admin, AdminModule.COURSES, AdminPermissionAction.READ));
        assertTrue(service.hasPermission(admin, AdminModule.COURSES, AdminPermissionAction.UPDATE));
        assertFalse(service.hasPermission(admin, AdminModule.COURSES, AdminPermissionAction.DELETE));
        assertFalse(service.hasPermission(admin, AdminModule.COURSES, AdminPermissionAction.CREATE));
        // A module with no grant = denied
        assertFalse(service.hasPermission(admin, AdminModule.USERS, AdminPermissionAction.READ));
    }

    // ---------- resolveEffectivePermissions ----------

    @Test
    void ownerResolvesFullAccessForEveryModule() {
        List<AdminModulePermissionDTO> perms = service.resolveEffectivePermissions(owner());
        assertEquals(AdminModule.values().length, perms.size());
        assertTrue(perms.stream().allMatch(p -> p.isCanRead() && p.isCanCreate() && p.isCanUpdate() && p.isCanDelete()));
    }

    @Test
    void learnerResolvesEmpty() {
        assertTrue(service.resolveEffectivePermissions(learner()).isEmpty());
    }

    @Test
    void scopedAdminResolvesGrantsPlusForcedDashboardRead() {
        User admin = adminWithGrants(AdminModulePermission.builder()
                .module(AdminModule.COURSES).canRead(true).build());
        List<AdminModulePermissionDTO> perms = service.resolveEffectivePermissions(admin);

        AdminModulePermissionDTO dashboard = perms.stream()
                .filter(p -> p.getModule().equals(AdminModule.DASHBOARD.name())).findFirst().orElseThrow();
        assertTrue(dashboard.isCanRead()); // forced on
        AdminModulePermissionDTO users = perms.stream()
                .filter(p -> p.getModule().equals(AdminModule.USERS.name())).findFirst().orElseThrow();
        assertFalse(users.isCanRead()); // not granted
    }

    // ---------- requirePermission / requireAdminOrOwner / requireOwner ----------

    @Test
    void requirePermissionUnknownUserThrowsNotFound() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.requirePermission("ghost", AdminModule.USERS, AdminPermissionAction.READ));
    }

    @Test
    void requirePermissionLearnerThrowsAdminRequired() {
        when(userRepository.findById("learner")).thenReturn(Optional.of(learner()));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requirePermission("learner", AdminModule.USERS, AdminPermissionAction.READ));
        assertTrue(ex.getMessage().contains("Admin access required"));
    }

    @Test
    void requirePermissionAdminWithoutGrantThrowsInsufficient() {
        User admin = adminWithGrants(AdminModulePermission.builder()
                .module(AdminModule.COURSES).canRead(true).build());
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.requirePermission("admin", AdminModule.USERS, AdminPermissionAction.DELETE));
        assertTrue(ex.getMessage().contains("Insufficient permission"));
    }

    @Test
    void requirePermissionGrantedAdminPasses() {
        User admin = adminWithGrants(AdminModulePermission.builder()
                .module(AdminModule.USERS).canRead(true).build());
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        service.requirePermission("admin", AdminModule.USERS, AdminPermissionAction.READ); // no throw
    }

    @Test
    void requireOwnerRejectsPlainAdmin() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(legacyAdmin()));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.requireOwner("admin"));
        assertTrue(ex.getMessage().contains("Owner access required"));
    }

    @Test
    void requireOwnerAllowsOwner() {
        when(userRepository.findById("owner")).thenReturn(Optional.of(owner()));
        service.requireOwner("owner"); // no throw
    }

    // ---------- updateAdminUserPermissions (owner-only + normalization) ----------

    @Test
    void updatePermissionsRequiresOwnerCaller() {
        when(userRepository.findById("admin")).thenReturn(Optional.of(legacyAdmin()));
        assertThrows(RuntimeException.class,
                () -> service.updateAdminUserPermissions("target", new UpdateAdminPermissionsRequestDTO(), "admin"));
    }

    @Test
    void updatePermissionsRejectsNonAdminTarget() {
        when(userRepository.findById("owner")).thenReturn(Optional.of(owner()));
        when(userRepository.findById("target")).thenReturn(Optional.of(learner()));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateAdminUserPermissions("target", new UpdateAdminPermissionsRequestDTO(), "owner"));
    }

    @Test
    void updatePermissionsNormalizesCreateImpliesReadAndDropsEmpty() {
        when(userRepository.findById("owner")).thenReturn(Optional.of(owner()));
        User target = User.builder().id("target").role(User.UserRole.ADMIN)
                .adminModulePermissions(new ArrayList<>()).build();
        when(userRepository.findById("target")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAdminPermissionsRequestDTO body = new UpdateAdminPermissionsRequestDTO();
        body.setPermissions(List.of(
                // create-only should be normalized to include read
                AdminModulePermissionDTO.builder().module("COURSES").canCreate(true).build(),
                // all-false entry should be dropped
                AdminModulePermissionDTO.builder().module("USERS").build(),
                // invalid module should be skipped
                AdminModulePermissionDTO.builder().module("NOT_A_MODULE").canRead(true).build()));

        AdminUserPermissionsDTO dto = service.updateAdminUserPermissions("target", body, "owner");

        // Persisted grants: only COURSES with read forced on
        List<AdminModulePermission> saved = target.getAdminModulePermissions();
        assertEquals(1, saved.size());
        assertEquals(AdminModule.COURSES, saved.get(0).getModule());
        assertTrue(saved.get(0).isCanRead());
        assertTrue(saved.get(0).isCanCreate());
        assertFalse(dto.isLegacyFullAccess()); // now has explicit grants
    }

    @Test
    void getAdminUserPermissionsRejectsNonAdmin() {
        when(userRepository.findById("learner")).thenReturn(Optional.of(learner()));
        assertThrows(IllegalArgumentException.class, () -> service.getAdminUserPermissions("learner"));
    }
}
