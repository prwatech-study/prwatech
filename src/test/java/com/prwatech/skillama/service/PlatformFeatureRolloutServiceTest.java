package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.UpdateFeatureRolloutDTO;
import com.prwatech.skillama.exception.FeatureNotLiveException;
import com.prwatech.skillama.model.PlatformFeature;
import com.prwatech.skillama.model.User;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformFeatureRolloutServiceTest {

    @Mock private PlatformFeatureRepository platformFeatureRepository;
    @Mock private SkillamaUserRepository userRepository;

    private PlatformFeatureRolloutService service;

    @BeforeEach
    void setUp() {
        service = new PlatformFeatureRolloutService(platformFeatureRepository, userRepository);
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
        User learner = new User();
        learner.setId("user-1");
        learner.setRole(User.UserRole.USER);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(learner));

        FeatureNotLiveException ex = assertThrows(
                FeatureNotLiveException.class,
                () -> service.assertAccessible("ai_interview", "user-1"));
        assertEquals("ai_interview", ex.getFeatureCode());
        assertEquals(FeatureNotLiveException.CODE, FeatureNotLiveException.CODE);
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
    void assertPubliclyLiveBlocksUpcoming() {
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(upcomingFeature()));
        assertThrows(
                FeatureNotLiveException.class,
                () -> service.assertPubliclyLive("ai_interview"));
    }

    @Test
    void assertPubliclyLiveAllowsLive() {
        PlatformFeature feature = upcomingFeature();
        feature.setRolloutStatus(PlatformFeature.RolloutStatus.LIVE);
        when(platformFeatureRepository.findByCode("ai_interview")).thenReturn(Optional.of(feature));
        service.assertPubliclyLive("ai_interview");
        assertTrue(service.isLive("ai_interview"));
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
