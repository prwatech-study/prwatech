package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.PublicStatsDTO;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Public homepage stats with a short in-memory cache to avoid Mongo on every visit.
 */
@Service
@RequiredArgsConstructor
public class PlatformPublicStatsService {

    private static final long CACHE_TTL_MS = 10 * 60 * 1000L; // 10 minutes

    private final SkillamaUserRepository userRepository;
    private final CourseService courseService;

    private volatile PublicStatsDTO cached;
    private volatile long cachedAtMs;

    public PublicStatsDTO getPublicStats() {
        long now = System.currentTimeMillis();
        PublicStatsDTO snapshot = cached;
        if (snapshot != null && now - cachedAtMs < CACHE_TTL_MS) {
            return snapshot;
        }
        synchronized (this) {
            if (cached != null && System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS) {
                return cached;
            }
            PublicStatsDTO fresh = loadStats();
            cached = fresh;
            cachedAtMs = System.currentTimeMillis();
            return fresh;
        }
    }

    private PublicStatsDTO loadStats() {
        long learnerCount = userRepository.findAll().stream()
                .filter(u -> u.getEffectiveRole() == User.UserRole.USER)
                .count();
        long publicCourseCount = courseService.findPublicCourses().size();
        long activeCourseCount = courseService.findAllActiveList().size();
        return PublicStatsDTO.builder()
                .learnerCount(learnerCount)
                .publicCourseCount(publicCourseCount)
                .activeCourseCount(activeCourseCount)
                .build();
    }
}
