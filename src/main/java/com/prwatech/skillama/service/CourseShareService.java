package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CourseShareHistoryItemDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseShareEvent;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.CourseShareEventRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class CourseShareService {

    /** Course sharing is restricted to these platforms — enforced here, not just hidden in the UI. */
    private static final Set<String> REWARDABLE_PLATFORMS = Set.of("INSTAGRAM", "LINKEDIN");
    private static final int USD_TO_CREDITS_RATE = 100;
    /** Reward before this became owner-tunable — used to backfill history rows saved before rewardUsd existed. */
    private static final double LEGACY_SHARE_REWARD_USD = 0.25;

    private final CourseShareEventRepository shareEventRepository;
    private final SkillamaUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final AiUsageService aiUsageService;

    private static int usdToCredits(double usd) {
        return (int) Math.round(usd * USD_TO_CREDITS_RATE);
    }

    /**
     * Records a course share and rewards credits the first time this user shares this course on
     * this platform. Repeat shares of the same (user, course, platform) are a no-op — the unique
     * index on CourseShareEvent backs this up against a concurrent duplicate request.
     *
     * @return the user's credit balance after this call
     */
    @Transactional
    public int trackShare(String userId, String courseId, String platform) {
        if (!StringUtils.hasText(courseId)) {
            throw new IllegalArgumentException("courseId is required");
        }
        if (!StringUtils.hasText(platform)) {
            throw new IllegalArgumentException("platform is required");
        }
        String normalizedPlatform = platform.trim().toUpperCase(Locale.ROOT);
        if (!REWARDABLE_PLATFORMS.contains(normalizedPlatform)) {
            throw new IllegalArgumentException("Unsupported share platform: " + normalizedPlatform);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        int currentCredits = user.getCredits() != null ? user.getCredits() : 0;

        if (shareEventRepository.existsByUserIdAndCourseIdAndPlatform(userId, courseId, normalizedPlatform)) {
            return currentCredits;
        }

        // Rate applied to THIS share, frozen onto the event — so an owner tuning
        // courseShareRewardUsd later never rewrites what past shares already earned.
        double rewardUsd = aiUsageService.loadSettings().getCourseShareRewardUsd();

        CourseShareEvent event = new CourseShareEvent();
        event.setUserId(userId);
        event.setCourseId(courseId);
        event.setPlatform(normalizedPlatform);
        event.setRewardUsd(rewardUsd);
        event.setCreatedAt(IndiaTime.now());
        try {
            shareEventRepository.save(event);
        } catch (DuplicateKeyException e) {
            // Concurrent duplicate share click raced us — already rewarded, don't double-credit.
            return currentCredits;
        }

        int updatedCredits = currentCredits + usdToCredits(rewardUsd);
        double currentShareBonus = user.getShareBonusUsd() != null ? user.getShareBonusUsd() : 0.0;
        user.setCredits(updatedCredits);
        // The spendable side of the reward — credits above is now just a display counter.
        user.setShareBonusUsd(currentShareBonus + rewardUsd);
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);
        return updatedCredits;
    }

    /** This user's course-share reward history, newest first — for the "My Shares" view. */
    public List<CourseShareHistoryItemDTO> getShareHistory(String userId) {
        List<CourseShareEvent> events = shareEventRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<String> courseIds = events.stream()
                .map(CourseShareEvent::getCourseId)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> courseNamesById = StreamSupport
                .stream(courseRepository.findAllById(courseIds).spliterator(), false)
                .collect(Collectors.toMap(Course::getId, Course::getName));

        return events.stream()
                .map(event -> CourseShareHistoryItemDTO.builder()
                        .courseId(event.getCourseId())
                        .courseName(courseNamesById.get(event.getCourseId()))
                        .platform(event.getPlatform())
                        // Pre-migration events have no rewardUsd stored — fall back to the
                        // fixed rate that was actually in effect before this became tunable.
                        .creditsEarned(usdToCredits(event.getRewardUsd() > 0 ? event.getRewardUsd() : LEGACY_SHARE_REWARD_USD))
                        .sharedAt(event.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
