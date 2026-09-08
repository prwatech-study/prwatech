package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.OrgHostResolveDTO;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationSecurity;
import com.prwatech.skillama.model.OrganizationStatus;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.OrganizationContractRenewalRepository;
import com.prwatech.skillama.repository.OrganizationContractRepository;
import com.prwatech.skillama.repository.OrganizationFeatureEntitlementRepository;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgLoginDiscoveryTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationContractRepository contractRepository;
    @Mock private OrganizationContractRenewalRepository renewalRepository;
    @Mock private OrganizationFeatureEntitlementRepository entitlementRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private PasswordEncode passwordEncode;
    @Mock private OrgFeatureService orgFeatureService;
    @Mock private OrgNotificationService orgNotificationService;
    @Mock private AdminAuditService adminAuditService;
    @Mock private OrgHierarchyService orgHierarchyService;
    @Mock private OrgPermissionService orgPermissionService;
    @Mock private OrgBrandingAssetService orgBrandingAssetService;

    @InjectMocks private OrganizationService organizationService;

    @Test
    void workEmailDomainOpensCorporateTenant() {
        Organization acme = Organization.builder()
                .id("org-1")
                .slug("acme")
                .name("Acme Corp")
                .status(OrganizationStatus.ACTIVE)
                .security(OrganizationSecurity.builder()
                        .allowedEmailDomains(List.of("acme.com"))
                        .build())
                .build();
        when(userRepository.findByEmail("owner@acme.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("owner@acme.com")).thenReturn(Optional.empty());
        when(organizationRepository.findAll()).thenReturn(List.of(acme));

        Optional<OrgHostResolveDTO> found = organizationService.discoverByWorkEmail("owner@acme.com");
        assertTrue(found.isPresent());
        assertEquals("acme", found.get().getSlug());
    }

    @Test
    void existingOrgMemberWinsEvenWithoutDomainMatch() {
        User member = User.builder()
                .id("u1")
                .email("dana@gmail.com")
                .organizationId("org-1")
                .build();
        Organization acme = Organization.builder()
                .id("org-1")
                .slug("acme")
                .name("Acme Corp")
                .status(OrganizationStatus.ACTIVE)
                .build();
        when(userRepository.findByEmail("dana@gmail.com")).thenReturn(Optional.of(member));
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(acme));

        Optional<OrgHostResolveDTO> found = organizationService.discoverByWorkEmail("dana@gmail.com");
        assertTrue(found.isPresent());
        assertEquals("acme", found.get().getSlug());
    }

    @Test
    void personalEmailStaysOnPublicLogin() {
        when(userRepository.findByEmail("you@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("you@gmail.com")).thenReturn(Optional.empty());
        when(organizationRepository.findAll()).thenReturn(List.of());

        assertTrue(organizationService.discoverByWorkEmail("you@gmail.com").isEmpty());
    }
}
