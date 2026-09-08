package com.prwatech.skillama.service;

import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSecurityServiceTest {

    @Mock private SkillamaUserRepository userRepository;
    @InjectMocks private TenantSecurityService tenantSecurityService;

    @Test
    void assertSameOrg_rejectsCrossOrgAccess() {
        User actor = User.builder().id("a1").organizationId("org-a").role(User.UserRole.USER).orgRole(OrgRole.MANAGER).build();
        User target = User.builder().id("b1").organizationId("org-b").role(User.UserRole.USER).build();

        assertThrows(IllegalStateException.class, () -> tenantSecurityService.assertSameOrg(actor, target));
    }

    @Test
    void assertUserInOrg_rejectsMismatchedOrg() {
        User user = User.builder().id("u1").organizationId("org-a").build();

        assertThrows(IllegalStateException.class, () -> tenantSecurityService.assertUserInOrg(user, "org-b"));
    }

    @Test
    void requireUser_returnsUserWhenFound() {
        User user = User.builder().id("u1").build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        User found = tenantSecurityService.requireUser("u1");
        assertEquals("u1", found.getId());
    }

    @Test
    void requireUser_throwsWhenMissing() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> tenantSecurityService.requireUser("missing"));
    }
}
