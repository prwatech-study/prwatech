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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reactivating a user consumes a seat, so it must respect the org seat cap. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgUserServiceSeatLimitTest {

    private static final String ORG_ID = "org-1";

    @Mock private SkillamaUserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PasswordEncode passwordEncode;
    @Mock private TenantSecurityService tenantSecurityService;
    @Mock private OrgHierarchyService orgHierarchyService;
    @Mock private OrgFeatureService orgFeatureService;
    @Mock private OrgNotificationService orgNotificationService;

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
                orgNotificationService);

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

    private User inactiveLearner() {
        return User.builder()
                .id("learner-1")
                .email("learner@acme.com")
                .organizationId(ORG_ID)
                .orgRole(OrgRole.LEARNER)
                .role(User.UserRole.USER)
                .active(false)
                .build();
    }

    @Test
    void reactivationIsRejectedWhenSeatsAreExhausted() {
        User target = inactiveLearner();
        when(userRepository.findById("learner-1")).thenReturn(Optional.of(target));
        when(orgFeatureService.getMaxSeats(ORG_ID)).thenReturn(5);
        when(userRepository.countByOrganizationIdAndActiveTrue(ORG_ID)).thenReturn(5L);

        UpdateOrgUserRequestDTO request = new UpdateOrgUserRequestDTO();
        request.setActive(true);

        assertThrows(IllegalStateException.class,
                () -> service.updateUser(admin(), "learner-1", request));

        assertFalse(target.isActive());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void reactivationSucceedsWhenSeatsRemain() {
        User target = inactiveLearner();
        when(userRepository.findById("learner-1")).thenReturn(Optional.of(target));
        when(orgFeatureService.getMaxSeats(ORG_ID)).thenReturn(5);
        when(userRepository.countByOrganizationIdAndActiveTrue(ORG_ID)).thenReturn(4L);

        UpdateOrgUserRequestDTO request = new UpdateOrgUserRequestDTO();
        request.setActive(true);

        service.updateUser(admin(), "learner-1", request);

        assertTrue(target.isActive());
        verify(userRepository).save(target);
    }

    @Test
    void deactivationIgnoresSeatCap() {
        User target = inactiveLearner();
        target.setActive(true);
        when(userRepository.findById("learner-1")).thenReturn(Optional.of(target));
        when(orgFeatureService.getMaxSeats(ORG_ID)).thenReturn(5);
        when(userRepository.countByOrganizationIdAndActiveTrue(ORG_ID)).thenReturn(5L);

        UpdateOrgUserRequestDTO request = new UpdateOrgUserRequestDTO();
        request.setActive(false);

        service.updateUser(admin(), "learner-1", request);

        assertFalse(target.isActive());
        verify(userRepository).save(target);
    }

    @Test
    void updatingAnAlreadyActiveUserDoesNotConsumeAnotherSeat() {
        User target = inactiveLearner();
        target.setActive(true);
        when(userRepository.findById("learner-1")).thenReturn(Optional.of(target));
        when(orgFeatureService.getMaxSeats(ORG_ID)).thenReturn(5);
        when(userRepository.countByOrganizationIdAndActiveTrue(ORG_ID)).thenReturn(5L);

        UpdateOrgUserRequestDTO request = new UpdateOrgUserRequestDTO();
        request.setActive(true);
        request.setName("Renamed Learner");

        service.updateUser(admin(), "learner-1", request);

        assertTrue(target.isActive());
        verify(userRepository).save(target);
    }
}
