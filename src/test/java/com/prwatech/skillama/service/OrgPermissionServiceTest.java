package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.OrgModulePermissionDTO;
import com.prwatech.skillama.dto.UpdateOrgPermissionsRequestDTO;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.OrgModule;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgPermissionServiceTest {

    @Mock private SkillamaUserRepository userRepository;

    private OrgPermissionService service;

    @BeforeEach
    void setUp() {
        service = new OrgPermissionService(userRepository);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User owner() {
        return User.builder().id("owner-1").organizationId("org-1").orgRole(OrgRole.ORG_OWNER)
                .role(User.UserRole.USER).build();
    }

    private User admin(List<com.prwatech.skillama.model.OrgModulePermission> grants) {
        return User.builder().id("admin-1").organizationId("org-1").orgRole(OrgRole.ORG_ADMIN)
                .role(User.UserRole.USER).orgModulePermissions(grants).build();
    }

    @Test
    void ownerAlwaysHasFullAccess() {
        assertTrue(service.hasPermission(owner(), OrgModule.USERS, AdminPermissionAction.CREATE));
        assertTrue(service.hasPermission(owner(), OrgModule.REPORTING, AdminPermissionAction.READ));
        assertFalse(service.usesLegacyFullAccess(owner()));
    }

    @Test
    void emptyAdminGrantsMeanLegacyFullAccess() {
        User admin = admin(List.of());
        assertTrue(service.usesLegacyFullAccess(admin));
        assertTrue(service.hasPermission(admin, OrgModule.USERS, AdminPermissionAction.CREATE));
    }

    @Test
    void newAdminHasNoAccessUntilGranted() {
        User admin = admin(OrgPermissionService.noAccessUntilGranted());
        assertFalse(service.usesLegacyFullAccess(admin));
        assertFalse(service.hasPermission(admin, OrgModule.USERS, AdminPermissionAction.READ));
        assertFalse(service.hasPermission(admin, OrgModule.REPORTING, AdminPermissionAction.READ));
    }

    @Test
    void ownerCanGrantUsersAndReportingToAdmin() {
        User target = admin(OrgPermissionService.noAccessUntilGranted());
        when(userRepository.findById("admin-1")).thenReturn(java.util.Optional.of(target));

        UpdateOrgPermissionsRequestDTO body = new UpdateOrgPermissionsRequestDTO();
        OrgModulePermissionDTO users = new OrgModulePermissionDTO();
        users.setModule("USERS");
        users.setCanRead(true);
        users.setCanCreate(true);
        users.setCanUpdate(true);
        OrgModulePermissionDTO reporting = new OrgModulePermissionDTO();
        reporting.setModule("REPORTING");
        reporting.setCanRead(true);
        body.setPermissions(List.of(users, reporting));

        service.updateAdminPermissions(owner(), "admin-1", body);

        assertTrue(service.hasPermission(target, OrgModule.USERS, AdminPermissionAction.CREATE));
        assertTrue(service.hasPermission(target, OrgModule.REPORTING, AdminPermissionAction.READ));
        assertFalse(service.hasPermission(target, OrgModule.SECURITY, AdminPermissionAction.UPDATE));
    }

    @Test
    void adminCannotGrantPermissions() {
        assertThrows(IllegalStateException.class,
                () -> service.listAdminsForOwner(admin(List.of())));
    }
}
