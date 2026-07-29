package com.prwatech.skillama.service;

import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.dto.UpdateUpgradeRequestDTO;
import com.prwatech.skillama.dto.UpgradeInterestRequestDTO;
import com.prwatech.skillama.dto.UpgradeRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.UpgradeRequest;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UpgradeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpgradeRequestServiceTest {

    @Mock private UpgradeRequestRepository upgradeRequestRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private EmailServiceImpl emailService;
    @Mock private SkillamaPlatformConfigService platformConfigService;
    @Mock private NotificationSettingsService notificationSettingsService;

    private UpgradeRequestService service;

    @BeforeEach
    void setUp() {
        service = new UpgradeRequestService(upgradeRequestRepository, userRepository,
                emailService, platformConfigService, notificationSettingsService);
        when(upgradeRequestRepository.save(any(UpgradeRequest.class))).thenAnswer(inv -> {
            UpgradeRequest r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId("req1");
            }
            return r;
        });
        when(platformConfigService.getUpgradeContactEmail()).thenReturn("sales@skillama.co.in");
    }

    private User freemiumUser() {
        return User.builder().id("u1").name("Asha").email("asha@x.com").phone("+919876543210")
                .planTier(User.PlanTier.FREEMIUM).build();
    }

    @Test
    void recordInterestUnknownUserThrows() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.recordInterest("ghost", new UpgradeInterestRequestDTO()));
    }

    @Test
    void recordInterestCreatesNewAndSnapshotsUserFields() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(freemiumUser()));

        UpgradeRequestDTO dto = service.recordInterest("u1", new UpgradeInterestRequestDTO());

        assertEquals("req1", dto.getId());
        assertEquals(UpgradeRequest.RequestStatus.NEW, dto.getStatus());
        assertEquals("PROFILE", dto.getSource());           // default source
        assertEquals("asha@x.com", dto.getUserEmail());
        assertEquals(User.PlanTier.FREEMIUM, dto.getPlanTier());  // snapshot from user
        verify(notificationSettingsService).sendTeamNotification(any(), any(), any());
    }

    @Test
    void recordInterestUsesRequestOverridesWhenPresent() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(freemiumUser()));
        UpgradeInterestRequestDTO req = new UpgradeInterestRequestDTO();
        req.setSource("BANNER");
        req.setCourseName("Data Science");
        req.setMessage("Please upgrade me");

        UpgradeRequestDTO dto = service.recordInterest("u1", req);

        assertEquals("BANNER", dto.getSource());
        assertEquals("Data Science", dto.getCourseName());
        assertEquals("Please upgrade me", dto.getMessage());
    }

    @Test
    void recordInterestSucceedsEvenIfNotificationFails() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(freemiumUser()));
        doThrow(new RuntimeException("smtp down"))
                .when(notificationSettingsService).sendTeamNotification(any(), any(), any());

        UpgradeRequestDTO dto = service.recordInterest("u1", new UpgradeInterestRequestDTO());
        assertNotNull(dto.getId()); // notification failure is swallowed
    }

    @Test
    void updateUnknownRequestThrows() {
        when(upgradeRequestRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.update("nope", new UpdateUpgradeRequestDTO(), "admin"));
    }

    @Test
    void updateToContactedStampsAdminAndTimestamp() {
        UpgradeRequest existing = UpgradeRequest.builder()
                .id("req1").userId("u1").status(UpgradeRequest.RequestStatus.NEW).build();
        when(upgradeRequestRepository.findById("req1")).thenReturn(Optional.of(existing));
        UpdateUpgradeRequestDTO body = new UpdateUpgradeRequestDTO();
        body.setStatus(UpgradeRequest.RequestStatus.CONTACTED);
        body.setNotes("called learner");

        UpgradeRequestDTO dto = service.update("req1", body, "admin7");

        assertEquals(UpgradeRequest.RequestStatus.CONTACTED, dto.getStatus());
        assertEquals("admin7", dto.getContactedByAdminId());
        assertNotNull(dto.getContactedAt());
        assertEquals("called learner", dto.getNotes());
    }

    @Test
    void listUsesSearchWhenProvided() {
        Page<UpgradeRequest> page = new PageImpl<>(List.of());
        when(upgradeRequestRepository.searchByUser(eq("asha"), any(Pageable.class))).thenReturn(page);
        service.list(0, 10, null, "asha");
        verify(upgradeRequestRepository).searchByUser(eq("asha"), any(Pageable.class));
    }

    @Test
    void listUsesStatusFilterWhenNoSearch() {
        Page<UpgradeRequest> page = new PageImpl<>(List.of());
        when(upgradeRequestRepository.findByStatusOrderByCreatedAtDesc(
                eq(UpgradeRequest.RequestStatus.NEW), any(Pageable.class))).thenReturn(page);
        service.list(0, 10, UpgradeRequest.RequestStatus.NEW, null);
        verify(upgradeRequestRepository).findByStatusOrderByCreatedAtDesc(
                eq(UpgradeRequest.RequestStatus.NEW), any(Pageable.class));
    }

    @Test
    void listFallsBackToAllWhenNoFilters() {
        Page<UpgradeRequest> page = new PageImpl<>(List.of());
        when(upgradeRequestRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);
        service.list(0, 10, null, "  ");
        verify(upgradeRequestRepository).findAllByOrderByCreatedAtDesc(any(Pageable.class));
    }
}
