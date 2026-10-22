package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.LearnerEngagementDTO;
import com.prwatech.skillama.dto.UpdateLearnerEngagementDTO;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Server-side learner memory + streak + locale. Source of truth for logged-in users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearnerEngagementService {

    private static final int MAX_FACTS = 12;
    private static final int MAX_WEAK = 8;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final UserProfileRepository userProfileRepository;
    private final SkillamaUserRepository userRepository;
    private final PlatformAiSettingsService platformAiSettingsService;
    private final NotificationSettingsService notificationSettingsService;
    private final SkillamaHtmlMailer skillamaHtmlMailer;
    private final AdminAuditService adminAuditService;

    @Value("${skillama.app.public-url:https://skillama.co.in}")
    private String publicUrl;

    public LearnerEngagementDTO getEngagement(String userId) {
        UserProfile profile = requireUserProfile(userId);
        return toDto(profile);
    }

    public LearnerEngagementDTO updateEngagement(String userId, UpdateLearnerEngagementDTO body) {
        if (body == null) {
            throw new IllegalArgumentException("body is required");
        }
        UserProfile profile = requireUserProfile(userId);
        ensureNested(profile);

        if (StringUtils.hasText(body.getPreferredLocale())) {
            String loc = body.getPreferredLocale().trim().toLowerCase();
            if ("hi".equals(loc)) {
                if (!platformAiSettingsService.isHindiTutorEnabled()) {
                    throw new IllegalArgumentException("Hindi tutor is not enabled on this platform");
                }
                profile.setPreferredLocale("hi");
            } else {
                profile.setPreferredLocale("en");
            }
            adminAuditService.log(
                    userId,
                    AdminAuditService.LEARNER_LOCALE_UPDATE,
                    "UserProfile",
                    profile.getId(),
                    "Preferred locale set to " + profile.getPreferredLocale(),
                    null);
        }

        UserProfile.LearnerMemory memory = profile.getLearnerMemory();
        boolean memoryChanged = false;
        if (body.getPreferredPace() != null) {
            memory.setPreferredPace(body.getPreferredPace().trim());
            memoryChanged = true;
        }
        if (body.getFacts() != null) {
            memory.setFacts(trimList(body.getFacts(), MAX_FACTS));
            memoryChanged = true;
        }
        if (body.getWeakTopics() != null) {
            memory.setWeakTopics(trimList(body.getWeakTopics(), MAX_WEAK));
            memoryChanged = true;
        }
        if (body.getSummary() != null) {
            String s = body.getSummary().trim();
            memory.setSummary(s.length() > 800 ? s.substring(0, 800) : s);
            memoryChanged = true;
        }
        if (memoryChanged) {
            memory.setUpdatedAt(IndiaTime.now());
            adminAuditService.log(
                    userId,
                    AdminAuditService.LEARNER_MEMORY_UPDATE,
                    "UserProfile",
                    profile.getId(),
                    "Learner memory updated",
                    null);
        }

        if (Boolean.TRUE.equals(body.getRecordActivity())) {
            recordActivityInternal(profile, userId);
        }

        profile.setUpdatedAt(IndiaTime.now());
        profile.setLastActivityAt(IndiaTime.now());
        userProfileRepository.save(profile);
        return toDto(profile);
    }

    /** Called from LMS when a lecture/quiz/exam/mentor activity happens. */
    public LearnerEngagementDTO recordActivity(String userId) {
        UserProfile profile = requireUserProfile(userId);
        ensureNested(profile);
        recordActivityInternal(profile, userId);
        profile.setUpdatedAt(IndiaTime.now());
        profile.setLastActivityAt(IndiaTime.now());
        userProfileRepository.save(profile);
        return toDto(profile);
    }

    /**
     * Merge weak topic from quiz remediation into server memory (additive).
     */
    public void recordWeakTopic(String userId, String topic) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(topic)) {
            return;
        }
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile == null || Boolean.TRUE.equals(profile.getIsGuest())) {
            return;
        }
        ensureNested(profile);
        String t = topic.trim();
        List<String> weak = new ArrayList<>(
                profile.getLearnerMemory().getWeakTopics() != null
                        ? profile.getLearnerMemory().getWeakTopics()
                        : List.of());
        weak.removeIf(t::equalsIgnoreCase);
        weak.add(0, t);
        profile.getLearnerMemory().setWeakTopics(trimList(weak, MAX_WEAK));
        profile.getLearnerMemory().setUpdatedAt(IndiaTime.now());
        profile.setUpdatedAt(IndiaTime.now());
        userProfileRepository.save(profile);
    }

    /** Compact context for AI requests (server truth). */
    public String buildContextPrompt(UserProfile profile) {
        if (profile == null) {
            return "";
        }
        ensureNested(profile);
        List<String> parts = new ArrayList<>();
        if ("hi".equalsIgnoreCase(profile.getPreferredLocale())
                && platformAiSettingsService.isHindiTutorEnabled()) {
            parts.add(
                    "Language preference: Hindi (हिन्दी). Prefer clear Hindi for explanations; "
                            + "keep code and established technical terms accurate in English when that preserves meaning. "
                            + "Do not invent facts.");
        }
        UserProfile.LearnerMemory m = profile.getLearnerMemory();
        if (StringUtils.hasText(m.getPreferredPace())) {
            parts.add("Preferred pace: " + m.getPreferredPace() + ".");
        }
        if (m.getWeakTopics() != null && !m.getWeakTopics().isEmpty()) {
            parts.add("Needs more practice on: "
                    + m.getWeakTopics().stream().limit(5).collect(Collectors.joining("; "))
                    + ".");
        }
        if (m.getFacts() != null && !m.getFacts().isEmpty()) {
            parts.add("About this learner: "
                    + m.getFacts().stream().limit(5).collect(Collectors.joining(" ")));
        }
        if (StringUtils.hasText(m.getSummary())) {
            parts.add("Memory summary: " + m.getSummary().trim());
        }
        return String.join(" ", parts).trim();
    }

    public String resolveTutorLocale(UserProfile profile) {
        if (profile == null) {
            return "en";
        }
        if ("hi".equalsIgnoreCase(profile.getPreferredLocale())
                && platformAiSettingsService.isHindiTutorEnabled()) {
            return "hi";
        }
        return "en";
    }

    /** Whether the platform owner has enabled Hindi tutor opt-in. */
    public boolean isHindiTutorAvailable() {
        return platformAiSettingsService.isHindiTutorEnabled();
    }

    /** Daily cron: remind learners at risk; notify when streak is lost. */
    public void processStreakEmails() {
        LocalDate today = IndiaTime.now().toLocalDate();
        String todayStr = today.format(DAY);
        String yesterdayStr = today.minusDays(1).format(DAY);

        for (UserProfile profile : userProfileRepository.findAll()) {
            if (profile.getUserId() == null || Boolean.TRUE.equals(profile.getIsGuest())) {
                continue;
            }
            ensureNested(profile);
            UserProfile.LearningStreak streak = profile.getLearningStreak();
            int current = streak.getCurrentStreak() != null ? streak.getCurrentStreak() : 0;
            String last = streak.getLastActiveDate();
            if (current <= 0 || !StringUtils.hasText(last)) {
                continue;
            }

            var user = userRepository.findById(profile.getUserId()).orElse(null);
            if (user == null || !StringUtils.hasText(user.getEmail()) || !user.isActive()) {
                continue;
            }

            try {
                if (last.equals(yesterdayStr)) {
                    // Active yesterday, not today → at risk reminder (once per day)
                    if (streak.getLastReminderSentAt() == null
                            || !streak.getLastReminderSentAt().toLocalDate().equals(today)) {
                        String name = user.getName() != null ? user.getName() : "there";
                        sendStreakEmail(
                                NotificationEventType.LEARNING_STREAK_REMINDER,
                                user.getEmail(),
                                "Your " + current + "-day streak is waiting",
                                "Hi " + name + ",\n\n"
                                        + "Your " + current + "-day streak is still alive, but only until the end of today.\n\n"
                                        + "Finish one short lecture or quiz and it continues.\n\n"
                                        + appUrl() + "/lms\n\n"
                                        + "— Skillama");
                        streak.setLastReminderSentAt(IndiaTime.now());
                        profile.setUpdatedAt(IndiaTime.now());
                        userProfileRepository.save(profile);
                        adminAuditService.log(
                                profile.getUserId(),
                                AdminAuditService.LEARNER_STREAK_REMINDER,
                                "UserProfile",
                                profile.getId(),
                                "Streak reminder emailed (streak=" + current + ")",
                                null);
                    }
                } else if (!last.equals(todayStr) && LocalDate.parse(last).isBefore(today.minusDays(1))) {
                    // Missed a day → streak lost
                    Integer notifiedFor = streak.getLostNotifiedForStreak();
                    if (notifiedFor == null || notifiedFor != current) {
                        String name = user.getName() != null ? user.getName() : "there";
                        sendStreakEmail(
                                NotificationEventType.LEARNING_STREAK_LOST,
                                user.getEmail(),
                                "Your " + current + "-day streak ended",
                                "Hi " + name + ",\n\n"
                                        + "Your " + current + "-day streak ended yesterday. "
                                        + "That run still counts toward your longest streak.\n\n"
                                        + "Start again today. One lecture is enough.\n\n"
                                        + appUrl() + "/lms\n\n"
                                        + "— Skillama");
                        streak.setLostNotifiedForStreak(current);
                        streak.setLastLostEmailSentAt(IndiaTime.now());
                        streak.setCurrentStreak(0);
                        profile.setUpdatedAt(IndiaTime.now());
                        userProfileRepository.save(profile);
                        adminAuditService.log(
                                profile.getUserId(),
                                AdminAuditService.LEARNER_STREAK_LOST,
                                "UserProfile",
                                profile.getId(),
                                "Streak lost email sent (was " + current + ")",
                                null);
                    }
                }
            } catch (Exception e) {
                log.warn("Streak email failed for user {}: {}", profile.getUserId(), e.getMessage());
            }
        }
    }

    private String appUrl() {
        String base = publicUrl == null || publicUrl.isBlank() ? "https://skillama.co.in" : publicUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private void sendStreakEmail(NotificationEventType type, String email, String subject, String body) {
        if (!notificationSettingsService.isEnabled(type)) {
            log.debug("Skipping disabled streak email: {}", type);
            return;
        }
        SkillamaHtmlMailer.MailSendResult result = skillamaHtmlMailer.sendMessage(email, subject, body);
        if (!result.isDelivered()) {
            log.warn("Streak email {} failed for {}: {}", type, email, result.getError());
        }
    }

    private void recordActivityInternal(UserProfile profile, String userId) {
        UserProfile.LearningStreak streak = profile.getLearningStreak();
        String today = IndiaTime.now().toLocalDate().format(DAY);
        if (today.equals(streak.getLastActiveDate())) {
            return;
        }
        int current = 1;
        if (streak.getLastActiveDate() != null) {
            LocalDate last = LocalDate.parse(streak.getLastActiveDate());
            if (last.equals(IndiaTime.now().toLocalDate().minusDays(1))) {
                current = (streak.getCurrentStreak() != null ? streak.getCurrentStreak() : 0) + 1;
            }
        }
        streak.setCurrentStreak(current);
        streak.setLongestStreak(
                Math.max(streak.getLongestStreak() != null ? streak.getLongestStreak() : 0, current));
        streak.setLastActiveDate(today);
        streak.setTotalActiveDays(
                (streak.getTotalActiveDays() != null ? streak.getTotalActiveDays() : 0) + 1);
        streak.setLostNotifiedForStreak(null);
        adminAuditService.log(
                userId,
                AdminAuditService.LEARNER_STREAK_UPDATE,
                "UserProfile",
                profile.getId(),
                "Learning activity recorded (streak=" + current + ")",
                null);
    }

    private UserProfile requireUserProfile(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("Authentication required");
        }
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User profile not found"));
    }

    private void ensureNested(UserProfile profile) {
        if (profile.getLearnerMemory() == null) {
            profile.setLearnerMemory(new UserProfile.LearnerMemory());
        }
        if (profile.getLearnerMemory().getFacts() == null) {
            profile.getLearnerMemory().setFacts(new ArrayList<>());
        }
        if (profile.getLearnerMemory().getWeakTopics() == null) {
            profile.getLearnerMemory().setWeakTopics(new ArrayList<>());
        }
        if (profile.getLearningStreak() == null) {
            profile.setLearningStreak(new UserProfile.LearningStreak());
        }
        if (!StringUtils.hasText(profile.getPreferredLocale())) {
            profile.setPreferredLocale("en");
        }
    }

    private LearnerEngagementDTO toDto(UserProfile profile) {
        ensureNested(profile);
        UserProfile.LearnerMemory m = profile.getLearnerMemory();
        UserProfile.LearningStreak s = profile.getLearningStreak();
        String today = IndiaTime.now().toLocalDate().format(DAY);
        boolean atRisk = s.getCurrentStreak() != null
                && s.getCurrentStreak() > 0
                && s.getLastActiveDate() != null
                && !today.equals(s.getLastActiveDate());

        return LearnerEngagementDTO.builder()
                .preferredLocale(resolveTutorLocale(profile))
                .hindiTutorAvailable(platformAiSettingsService.isHindiTutorEnabled())
                .memory(LearnerEngagementDTO.LearnerMemoryDTO.builder()
                        .preferredPace(m.getPreferredPace())
                        .facts(m.getFacts() != null ? m.getFacts() : List.of())
                        .weakTopics(m.getWeakTopics() != null ? m.getWeakTopics() : List.of())
                        .summary(m.getSummary())
                        .updatedAt(m.getUpdatedAt())
                        .contextPrompt(buildContextPrompt(profile))
                        .build())
                .streak(LearnerEngagementDTO.LearningStreakDTO.builder()
                        .currentStreak(s.getCurrentStreak() != null ? s.getCurrentStreak() : 0)
                        .longestStreak(s.getLongestStreak() != null ? s.getLongestStreak() : 0)
                        .lastActiveDate(s.getLastActiveDate())
                        .totalActiveDays(s.getTotalActiveDays() != null ? s.getTotalActiveDays() : 0)
                        .atRisk(atRisk)
                        .build())
                .build();
    }

    private static List<String> trimList(List<String> in, int max) {
        return in.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(max)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
