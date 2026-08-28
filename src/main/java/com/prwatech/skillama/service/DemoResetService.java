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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-only reset for the shared investor-demo learner account: wipes the
 * activity that accumulates during a pitch (doubts, quiz/exam attempts and
 * sessions, chat history) and reseeds dashboard progress via
 * {@link DemoDashboardSeedService}.
 *
 * Deliberately never touches the AI wallet: {@code aiCostUsdThisPeriod},
 * {@code aiCostPeriodStart}, {@code aiWalletLimitUsd}, bonuses and
 * {@code ai_usage_events} are lifetime records that must never be reset
 * (see the ground rule on AiUsageService.ensureUsageAnchor). The User
 * document is not saved at all, and {@code tokenVersion} is untouched so an
 * in-flight demo session survives a reset.
 */
@Service
@RequiredArgsConstructor
public class DemoResetService {

    private final AdminService adminService;
    private final SkillamaUserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final DoubtRepository doubtRepository;
    private final ModuleQuizAttemptRepository moduleQuizAttemptRepository;
    private final ModuleQuizSessionRepository moduleQuizSessionRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ExamSessionRepository examSessionRepository;
    private final ExamRecommendationLogRepository examRecommendationLogRepository;
    private final AiAnswerFeedbackRepository aiAnswerFeedbackRepository;
    private final DemoDashboardSeedService demoDashboardSeedService;

    @Transactional
    public DemoResetResultDTO resetForUser(String userId, String ownerId) {
        adminService.requireOwner(ownerId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getRole() == User.UserRole.ADMIN || user.getRole() == User.UserRole.OWNER) {
            throw new IllegalArgumentException(
                    "Demo reset applies to learner (USER) accounts. Use a learner login for demos.");
        }

        long deletedDoubts = safeCount(doubtRepository.deleteByUserId(userId));
        long deletedQuizAttempts = safeCount(moduleQuizAttemptRepository.deleteByUserId(userId));
        long deletedQuizSessions = safeCount(moduleQuizSessionRepository.deleteByUserId(userId));
        long deletedExamAttempts = safeCount(examAttemptRepository.deleteByUserId(userId));
        long deletedExamSessions = safeCount(examSessionRepository.deleteByUserId(userId));
        long deletedRecommendationLogs = safeCount(examRecommendationLogRepository.deleteByUserId(userId));
        long deletedAnswerFeedback = safeCount(aiAnswerFeedbackRepository.deleteByUserId(userId));

        int clearedChatInteractions = 0;
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null && profile.getChatInteractions() != null
                && !profile.getChatInteractions().isEmpty()) {
            clearedChatInteractions = profile.getChatInteractions().size();
            profile.getChatInteractions().clear();
            userProfileRepository.save(profile);
        }

        // assignAll=false keeps the curated limited-enrollment set intact.
        DemoDashboardSeedResultDTO seedResult =
                demoDashboardSeedService.seedForEmail(user.getEmail(), ownerId, false);

        return DemoResetResultDTO.builder()
                .userId(userId)
                .email(user.getEmail())
                .deletedDoubts(deletedDoubts)
                .deletedQuizAttempts(deletedQuizAttempts)
                .deletedQuizSessions(deletedQuizSessions)
                .deletedExamAttempts(deletedExamAttempts)
                .deletedExamSessions(deletedExamSessions)
                .deletedRecommendationLogs(deletedRecommendationLogs)
                .deletedAnswerFeedback(deletedAnswerFeedback)
                .clearedChatInteractions(clearedChatInteractions)
                .seedResult(seedResult)
                .build();
    }

    private long safeCount(Long count) {
        return count != null ? count : 0L;
    }
}
