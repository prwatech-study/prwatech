package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.UpdateFeatureRolloutDTO;
import com.prwatech.skillama.exception.FeatureNotLiveException;
import com.prwatech.skillama.model.PlatformFeature;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AiInterviewScheduleRepository;
import com.prwatech.skillama.repository.PlatformFeatureRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformFeatureRolloutServiceTest {

    @Mock private PlatformFeatureRepository platformFeatureRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private AiInterviewScheduleRepository aiInterviewScheduleRepository;

    private PlatformFeatureRolloutService service;

    @BeforeEach
    void setUp() {
        service = new PlatformFeatureRolloutService(
                platformFeatureRepository, userRepository, aiInterviewScheduleRepository);
    }

    @Test
    void assertAccessibleAllowsEveryoneWhenLive() {
        PlatformFeature feature = upcomingFeature();
        feature.setRolloutStatus(PlatformFeature.RolloutStatus.LIVE);
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(feature));

        service.assertAccessible("ai_interview", "user-1");
    }

    @Test
    void assertAccessibleBlocksUserWhenUpcoming() {
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(upcomingFeature()));
        User learner = learner("user-1", "learner@example.com");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(learner));
        when(aiInterviewScheduleRepository.existsByCandidateEmail("learner@example.com")).thenReturn(false);

        FeatureNotLiveException ex = assertThrows(
                FeatureNotLiveException.class,
                () -> service.assertAccessible("ai_interview", "user-1"));
        assertEquals("ai_interview", ex.getFeatureCode());
    }

    @Test
    void assertAccessibleAllowsAssignedCandidateWhenUpcoming() {
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(upcomingFeature()));
        User learner = learner("user-1", "candidate@example.com");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(learner));
        when(aiInterviewScheduleRepository.existsByCandidateEmail("candidate@example.com")).thenReturn(true);

        service.assertAccessible("ai_interview", "user-1");
    }

    @Test
    void assertAccessibleAllowsPilotWhenUpcoming() {
        PlatformFeature feature = upcomingFeature();
        feature.setCode(PlatformFeatureRolloutService.AI_MOCK_INTERVIEW);
        feature.setPilotUserIds(List.of("pilot-1"));
        when(platformFeatureRepository.findByCode(PlatformFeatureRolloutService.AI_MOCK_INTERVIEW))
                .thenReturn(Optional.of(feature));
        User learner = learner("pilot-1", "pilot@example.com");
        when(userRepository.findById("pilot-1")).thenReturn(Optional.of(learner));

        service.assertAccessible(PlatformFeatureRolloutService.AI_MOCK_INTERVIEW, "pilot-1");
    }

    @Test
    void assertAccessibleBlocksNonPilotMockWhenUpcoming() {
        PlatformFeature feature = upcomingFeature();
        feature.setCode(PlatformFeatureRolloutService.AI_MOCK_INTERVIEW);
        feature.setPilotUserIds(List.of("pilot-1"));
        when(platformFeatureRepository.findByCode(PlatformFeatureRolloutService.AI_MOCK_INTERVIEW))
                .thenReturn(Optional.of(feature));
        User learner = learner("user-2", "other@example.com");
        when(userRepository.findById("user-2")).thenReturn(Optional.of(learner));

        assertThrows(
                FeatureNotLiveException.class,
                () -> service.assertAccessible(
                        PlatformFeatureRolloutService.AI_MOCK_INTERVIEW, "user-2"));
    }

    @Test
    void assertAccessibleAllowsStaffWhenUpcoming() {
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(upcomingFeature()));
        for (User.UserRole role : new User.UserRole[] {
                User.UserRole.ADMIN, User.UserRole.OWNER, User.UserRole.TESTER
        }) {
            User staff = new User();
            staff.setId("staff-" + role);
            staff.setRole(role);
            when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
            service.assertAccessible("ai_interview", staff.getId());
        }
    }

    @Test
    void assertAccessibleBlocksUserWhenHidden() {
        PlatformFeature feature = upcomingFeature();
        feature.setCode(PlatformFeatureRolloutService.GLOBAL_CERTIFICATION);
        feature.setRolloutStatus(PlatformFeature.RolloutStatus.HIDDEN);
        when(platformFeatureRepository.findByCode(PlatformFeatureRolloutService.GLOBAL_CERTIFICATION))
                .thenReturn(Optional.of(feature));
        User learner = learner("user-1", "learner@example.com");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(learner));

        FeatureNotLiveException ex = assertThrows(
                FeatureNotLiveException.class,
                () -> service.assertAccessible(
                        PlatformFeatureRolloutService.GLOBAL_CERTIFICATION, "user-1"));
        assertEquals(PlatformFeatureRolloutService.GLOBAL_CERTIFICATION, ex.getFeatureCode());
    }

    @Test
    void assertAccessibleAllowsStaffWhenHidden() {
        PlatformFeature feature = upcomingFeature();
        feature.setCode(PlatformFeatureRolloutService.GLOBAL_CERTIFICATION);
        feature.setRolloutStatus(PlatformFeature.RolloutStatus.HIDDEN);
        when(platformFeatureRepository.findByCode(PlatformFeatureRolloutService.GLOBAL_CERTIFICATION))
                .thenReturn(Optional.of(feature));
        for (User.UserRole role : new User.UserRole[] {
                User.UserRole.ADMIN, User.UserRole.OWNER, User.UserRole.TESTER
        }) {
            User staff = new User();
            staff.setId("staff-" + role);
            staff.setRole(role);
            when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
            service.assertAccessible(PlatformFeatureRolloutService.GLOBAL_CERTIFICATION, staff.getId());
        }
    }

    @Test
    void updateRolloutToLiveSetsLiveAt() {
        PlatformFeature feature = upcomingFeature();
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(feature));
        when(platformFeatureRepository.save(any(PlatformFeature.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateFeatureRolloutDTO body = new UpdateFeatureRolloutDTO();
        body.setRolloutStatus(PlatformFeature.RolloutStatus.LIVE);

        var dto = service.updateRollout("ai_interview", body, "owner-1");

        assertEquals(PlatformFeature.RolloutStatus.LIVE, dto.getRolloutStatus());
        assertNotNull(dto.getLiveAt());
        ArgumentCaptor<PlatformFeature> captor = ArgumentCaptor.forClass(PlatformFeature.class);
        verify(platformFeatureRepository).save(captor.capture());
        assertEquals(PlatformFeature.RolloutStatus.LIVE, captor.getValue().getRolloutStatus());
        assertNotNull(captor.getValue().getLiveAt());
    }

    @Test
    void updateRolloutPersistsPilotUserIdsWithoutClearingOnLeaveLive() {
        PlatformFeature feature = upcomingFeature();
        feature.setCode(PlatformFeatureRolloutService.AI_MOCK_INTERVIEW);
        feature.setRolloutStatus(PlatformFeature.RolloutStatus.LIVE);
        feature.setLiveAt(IndiaTime.now().minusDays(1));
        feature.setPilotUserIds(List.of("keep-me"));
        when(platformFeatureRepository.findByCode(PlatformFeatureRolloutService.AI_MOCK_INTERVIEW))
                .thenReturn(Optional.of(feature));
        when(platformFeatureRepository.save(any(PlatformFeature.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateFeatureRolloutDTO body = new UpdateFeatureRolloutDTO();
        body.setRolloutStatus(PlatformFeature.RolloutStatus.UPCOMING);
        body.setPilotUserIds(List.of("pilot-a", "pilot-b"));

        var dto = service.updateRollout(
                PlatformFeatureRolloutService.AI_MOCK_INTERVIEW, body, "owner-1");

        assertEquals(PlatformFeature.RolloutStatus.UPCOMING, dto.getRolloutStatus());
        assertEquals(List.of("pilot-a", "pilot-b"), dto.getPilotUserIds());
        assertNull(dto.getLiveAt());
    }

    @Test
    void updateRolloutLeavingLiveClearsLiveAt() {
        PlatformFeature feature = upcomingFeature();
        feature.setRolloutStatus(PlatformFeature.RolloutStatus.LIVE);
        feature.setLiveAt(IndiaTime.now().minusDays(10));
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(feature));
        when(platformFeatureRepository.save(any(PlatformFeature.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateFeatureRolloutDTO body = new UpdateFeatureRolloutDTO();
        body.setRolloutStatus(PlatformFeature.RolloutStatus.UPCOMING);

        var dto = service.updateRollout("ai_interview", body, "owner-1");

        assertEquals(PlatformFeature.RolloutStatus.UPCOMING, dto.getRolloutStatus());
        assertNull(dto.getLiveAt());
        assertFalse(dto.isNewBadge());
    }

    @Test
    void listLearnerCatalogShowsLiveForPilotViewer() {
        PlatformFeature feature = upcomingFeature();
        feature.setCode(PlatformFeatureRolloutService.AI_MOCK_INTERVIEW);
        feature.setPilotUserIds(List.of("pilot-1"));
        when(platformFeatureRepository.findByLearnerVisibleTrueAndActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(feature));

        var list = service.listLearnerCatalog("pilot-1");
        assertEquals(1, list.size());
        assertEquals(PlatformFeature.RolloutStatus.LIVE, list.get(0).getRolloutStatus());
    }

    @Test
    void listLearnerCatalogKeepsUpcomingForAnonymous() {
        PlatformFeature feature = upcomingFeature();
        when(platformFeatureRepository.findByLearnerVisibleTrueAndActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(feature));

        var list = service.listLearnerCatalog(null);
        assertEquals(PlatformFeature.RolloutStatus.UPCOMING, list.get(0).getRolloutStatus());
    }

    @Test
    void isNewTrueWithinNinetyDays() {
        PlatformFeature feature = upcomingFeature();
        feature.setRolloutStatus(PlatformFeature.RolloutStatus.LIVE);
        feature.setLiveAt(IndiaTime.now().minusDays(30));
        assertTrue(service.isNew(feature));
    }

    @Test
    void isNewFalseAfterNinetyDays() {
        PlatformFeature feature = upcomingFeature();
        feature.setRolloutStatus(PlatformFeature.RolloutStatus.LIVE);
        feature.setLiveAt(IndiaTime.now().minusDays(91));
        assertFalse(service.isNew(feature));
    }

    @Test
    void isNewFalseWhenUpcoming() {
        PlatformFeature feature = upcomingFeature();
        feature.setLiveAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        assertFalse(service.isNew(feature));
    }

    @Test
    void assertPubliclyLiveBlocksUpcomingWithoutToken() {
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(upcomingFeature()));
        assertThrows(
                FeatureNotLiveException.class,
                () -> service.assertPubliclyLive("ai_interview"));
    }

    @Test
    void assertPubliclyLiveAllowsValidInviteTokenWhenUpcoming() {
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(upcomingFeature()));
        when(aiInterviewScheduleRepository.existsByInviteToken(eq("tok-1"))).thenReturn(true);
        service.assertPubliclyLive("ai_interview", "tok-1");
    }

    @Test
    void assertPubliclyLiveBlocksInvalidInviteTokenWhenUpcoming() {
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(upcomingFeature()));
        when(aiInterviewScheduleRepository.existsByInviteToken(eq("bad"))).thenReturn(false);
        assertThrows(
                FeatureNotLiveException.class,
                () -> service.assertPubliclyLive("ai_interview", "bad"));
    }

    @Test
    void assertPubliclyLiveAllowsLive() {
        PlatformFeature feature = upcomingFeature();
        feature.setRolloutStatus(PlatformFeature.RolloutStatus.LIVE);
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(feature));
        service.assertPubliclyLive("ai_interview");
        assertTrue(service.isLive("ai_interview"));
    }

    private static User learner(String id, String email) {
        User learner = new User();
        learner.setId(id);
        learner.setEmail(email);
        learner.setRole(User.UserRole.USER);
        return learner;
    }

    private PlatformFeature upcomingFeature() {
        return PlatformFeature.builder()
                .code("ai_interview")
                .name("AI Interview")
                .rolloutStatus(PlatformFeature.RolloutStatus.UPCOMING)
                .learnerVisible(true)
                .active(true)
                .build();
    }
}
