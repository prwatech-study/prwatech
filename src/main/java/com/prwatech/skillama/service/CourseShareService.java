package com.prwatech.skillama.service;

import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CourseShareEvent;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseShareEventRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CourseShareService {

    /** Course sharing is restricted to these platforms — enforced here, not just hidden in the UI. */
    private static final Set<String> REWARDABLE_PLATFORMS = Set.of("INSTAGRAM", "LINKEDIN");
    public static final int SHARE_REWARD_CREDITS = 25;

    private final CourseShareEventRepository shareEventRepository;
    private final SkillamaUserRepository userRepository;

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

        CourseShareEvent event = new CourseShareEvent();
        event.setUserId(userId);
        event.setCourseId(courseId);
        event.setPlatform(normalizedPlatform);
        event.setCreatedAt(IndiaTime.now());
        try {
            shareEventRepository.save(event);
        } catch (DuplicateKeyException e) {
            // Concurrent duplicate share click raced us — already rewarded, don't double-credit.
            return currentCredits;
        }

        int updatedCredits = currentCredits + SHARE_REWARD_CREDITS;
        user.setCredits(updatedCredits);
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);
        return updatedCredits;
    }
}
