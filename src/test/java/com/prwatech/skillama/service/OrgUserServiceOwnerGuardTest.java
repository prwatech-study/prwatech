package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.UpdateOrgUserRequestDTO;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.common.configuration.PasswordEncode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgUserServiceOwnerGuardTest {

    private static final String ORG_ID = "org-1";

    @Mock private SkillamaUserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PasswordEncode passwordEncode;
    @Mock private TenantSecurityService tenantSecurityService;
    @Mock private OrgHierarchyService orgHierarchyService;
    @Mock private OrgFeatureService orgFeatureService;
    @Mock private OrgNotificationService orgNotificationService;
    @Mock private OrgDepartmentService orgDepartmentService;
    @Mock private OrgPermissionService orgPermissionService;

    private OrgUserService service;

    @BeforeEach
    void setUp() {
        service = new OrgUserService(
                userRepository,
                organizationRepository,
                passwordEncode,
                tenantSecurityService,
                orgHierarchyService,
                orgFeatureService,
                orgNotificationService,
                orgDepartmentService,
                orgPermissionService);

        Organization org = Organization.builder().id(ORG_ID).name("Acme").slug("acme").build();
        when(tenantSecurityService.requireActiveOrganization(ORG_ID)).thenReturn(org);
        when(orgFeatureService.isEnabled(ORG_ID, "org_user_management")).thenReturn(true);
        when(userRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User admin() {
        return User.builder()
                .id("admin-1")
                .organizationId(ORG_ID)
                .orgRole(OrgRole.ORG_ADMIN)
                .role(User.UserRole.USER)
                .build();
    }

    private User owner() {
        return User.builder()
                .id("owner-1")
                .name("Alex Owner")
                .email("owner@acme.com")
                .organizationId(ORG_ID)
                .orgRole(OrgRole.ORG_OWNER)
                .role(User.UserRole.USER)
                .active(true)
                .build();
    }

    @Test
    void orgAdminCannotEditTheOwner() {
        User target = owner();
        when(userRepository.findById("owner-1")).thenReturn(Optional.of(target));

        UpdateOrgUserRequestDTO request = new UpdateOrgUserRequestDTO();
        request.setOrgRole(OrgRole.LEARNER);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.updateUser(admin(), "owner-1", request));
        assertEquals("Organization admins cannot edit the owner", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void ownerCanUpdateOwnNameWithoutChangingRole() {
        User target = owner();
        when(userRepository.findById("owner-1")).thenReturn(Optional.of(target));

        UpdateOrgUserRequestDTO request = new UpdateOrgUserRequestDTO();
        request.setName("Alex Updated");

        service.updateUser(owner(), "owner-1", request);

        assertEquals("Alex Updated", target.getName());
        assertEquals(OrgRole.ORG_OWNER, target.getOrgRole());
        verify(userRepository).save(target);
    }
}
