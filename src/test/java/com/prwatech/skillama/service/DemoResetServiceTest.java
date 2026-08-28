package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.DemoDashboardSeedResultDTO;
import com.prwatech.skillama.dto.DemoResetResultDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.AiAnswerFeedbackRepository;
import com.prwatech.skillama.repository.DoubtRepository;
import com.prwatech.skillama.repository.ExamAttemptRepository;
import com.prwatech.skillama.repository.ExamRecommendationLogRepository;
import com.prwatech.skillama.repository.ExamSessionRepository;
import com.prwatech.skillama.repository.ModuleQuizAttemptRepository;
import com.prwatech.skillama.repository.ModuleQuizSessionRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoResetServiceTest {

    private static final String USER_ID = "demo-1";
    private static final String OWNER_ID = "owner-1";
    private static final String DEMO_EMAIL = "demo@skillama.co.in";

    @Mock private AdminService adminService;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private DoubtRepository doubtRepository;
    @Mock private ModuleQuizAttemptRepository moduleQuizAttemptRepository;
    @Mock private ModuleQuizSessionRepository moduleQuizSessionRepository;
    @Mock private ExamAttemptRepository examAttemptRepository;
    @Mock private ExamSessionRepository examSessionRepository;
    @Mock private ExamRecommendationLogRepository examRecommendationLogRepository;
    @Mock private AiAnswerFeedbackRepository aiAnswerFeedbackRepository;
    @Mock private DemoDashboardSeedService demoDashboardSeedService;

    @InjectMocks private DemoResetService demoResetService;

    private User demoUser;

    @BeforeEach
    void setUp() {
        demoUser = User.builder()
                .id(USER_ID)
                .email(DEMO_EMAIL)
                .role(User.UserRole.USER)
                .active(true)
                .build();
    }

    @Test
    void resetForUser_deletesActivityAndReseedsWithoutAssignAll() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(demoUser));
        when(doubtRepository.deleteByUserId(USER_ID)).thenReturn(3L);
        when(moduleQuizAttemptRepository.deleteByUserId(USER_ID)).thenReturn(4L);
        when(moduleQuizSessionRepository.deleteByUserId(USER_ID)).thenReturn(1L);
        when(examAttemptRepository.deleteByUserId(USER_ID)).thenReturn(2L);
        when(examSessionRepository.deleteByUserId(USER_ID)).thenReturn(1L);
        when(examRecommendationLogRepository.deleteByUserId(USER_ID)).thenReturn(2L);
        when(aiAnswerFeedbackRepository.deleteByUserId(USER_ID)).thenReturn(5L);
        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        DemoDashboardSeedResultDTO seed = DemoDashboardSeedResultDTO.builder()
                .userId(USER_ID).email(DEMO_EMAIL).coursesSeeded(2).build();
        when(demoDashboardSeedService.seedForEmail(DEMO_EMAIL, OWNER_ID, false)).thenReturn(seed);

        DemoResetResultDTO result = demoResetService.resetForUser(USER_ID, OWNER_ID);

        assertEquals(3L, result.getDeletedDoubts());
        assertEquals(4L, result.getDeletedQuizAttempts());
        assertEquals(1L, result.getDeletedQuizSessions());
        assertEquals(2L, result.getDeletedExamAttempts());
        assertEquals(1L, result.getDeletedExamSessions());
        assertEquals(2L, result.getDeletedRecommendationLogs());
        assertEquals(5L, result.getDeletedAnswerFeedback());
        assertEquals(seed, result.getSeedResult());
        verify(demoDashboardSeedService).seedForEmail(DEMO_EMAIL, OWNER_ID, false);
    }

    @Test
    void resetForUser_clearsChatInteractions() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(demoUser));
        UserProfile profile = new UserProfile();
        profile.getChatInteractions().add(UserProfile.ChatInteraction.builder().id("c1").build());
        profile.getChatInteractions().add(UserProfile.ChatInteraction.builder().id("c2").build());
        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(demoDashboardSeedService.seedForEmail(DEMO_EMAIL, OWNER_ID, false))
                .thenReturn(DemoDashboardSeedResultDTO.builder().userId(USER_ID).build());

        DemoResetResultDTO result = demoResetService.resetForUser(USER_ID, OWNER_ID);

        assertEquals(2, result.getClearedChatInteractions());
        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(saved.capture());
        assertTrue(saved.getValue().getChatInteractions().isEmpty());
    }

    /** Ground rule: credits are lifetime — a demo reset must never write the User document. */
    @Test
    void resetForUser_neverSavesUserOrTouchesWalletFields() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(demoUser));
        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(demoDashboardSeedService.seedForEmail(DEMO_EMAIL, OWNER_ID, false))
                .thenReturn(DemoDashboardSeedResultDTO.builder().userId(USER_ID).build());

        demoResetService.resetForUser(USER_ID, OWNER_ID);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetForUser_nonOwner_forbidden() {
        doThrow(new RuntimeException("Owner access required"))
                .when(adminService).requireOwner("not-owner");

        assertThrows(RuntimeException.class,
                () -> demoResetService.resetForUser(USER_ID, "not-owner"));
        verify(doubtRepository, never()).deleteByUserId(anyString());
        verify(demoDashboardSeedService, never()).seedForEmail(anyString(), anyString(), anyBoolean());
    }

    @Test
    void resetForUser_adminOrOwnerTarget_rejected() {
        demoUser.setRole(User.UserRole.OWNER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(demoUser));

        assertThrows(IllegalArgumentException.class,
                () -> demoResetService.resetForUser(USER_ID, OWNER_ID));
        verify(doubtRepository, never()).deleteByUserId(anyString());
    }

    @Test
    void resetForUser_unknownUser_notFound() {
        when(userRepository.findById("nope")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> demoResetService.resetForUser("nope", OWNER_ID));
    }
}
