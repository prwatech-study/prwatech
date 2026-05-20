package com.prwatech.skillama.service;

import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserCourseAccessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserCourseAccessService.class);

    private final CourseRepository courseRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserCourseProgressRepository progressRepository;
    private final SkillamaUserRepository userRepository;

    public Optional<Course> findDefaultFreemiumCourse() {
        return courseRepository.findByIsDefaultFreemiumCourseTrue();
    }

    @Transactional
    public boolean setDefaultFreemiumCourse(String courseId) {
        return courseRepository.findById(courseId)
                .map(course -> {
                    courseRepository.findByIsDefaultFreemiumCourseTrue()
                            .ifPresent(existing -> {
                                if (!existing.getId().equals(courseId)) {
                                    existing.setIsDefaultFreemiumCourse(false);
                                    existing.setUpdatedAt(LocalDateTime.now());
                                    courseRepository.save(existing);
                                }
                            });
                    course.setIsDefaultFreemiumCourse(true);
                    course.setUpdatedAt(LocalDateTime.now());
                    courseRepository.save(course);
                    LOGGER.info("Set course '{}' as default freemium course", course.getName());
                    return true;
                })
                .orElse(false);
    }

    public boolean isAdminOrOwner(String userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRole() == User.UserRole.ADMIN || u.getRole() == User.UserRole.OWNER)
                .orElse(false);
    }

    public boolean hasActiveEnrollment(String userId, String courseId) {
        return enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .map(e -> e.getStatus() == UserCourseEnrollment.EnrollmentStatus.ACTIVE)
                .orElse(false);
    }

    public void assertCanAccessCourse(String userId, String courseId) {
        if (isAdminOrOwner(userId)) {
            return;
        }
        if (!hasActiveEnrollment(userId, courseId)) {
            throw new ForbiddenException("You do not have access to this course. Contact your administrator.");
        }
    }

    @Transactional
    public UserCourseEnrollment enrollIfAbsent(
            String userId,
            String courseId,
            UserCourseEnrollment.EnrollmentType type) {
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            return enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                    .map(existing -> {
                        if (existing.getStatus() != UserCourseEnrollment.EnrollmentStatus.ACTIVE) {
                            existing.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
                            existing.setEnrolledAt(LocalDateTime.now());
                            enrollmentRepository.save(existing);
                        }
                        return existing;
                    })
                    .orElseThrow();
        }
        LocalDateTime now = LocalDateTime.now();
        UserCourseEnrollment enrollment = new UserCourseEnrollment();
        enrollment.setUserId(userId);
        enrollment.setCourseId(courseId);
        enrollment.setEnrollmentType(type);
        enrollment.setEnrolledAt(now);
        enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
        enrollmentRepository.save(enrollment);
        initializeProgress(userId, courseId);
        return enrollment;
    }

    @Transactional
    public Optional<UserCourseEnrollment> enrollDefaultFreemiumCourse(String userId) {
        return findDefaultFreemiumCourse()
                .map(course -> enrollIfAbsent(
                        userId,
                        course.getId(),
                        UserCourseEnrollment.EnrollmentType.DEFAULT_FREEMIUM));
    }

    @Transactional
    public void ensureDefaultFreemiumEnrollment(String userId) {
        enrollDefaultFreemiumCourse(userId);
    }

    @Transactional
    public void touchLastAccessed(String userId, String courseId) {
        progressRepository.findByUserIdAndCourseId(userId, courseId).ifPresent(progress -> {
            progress.setLastAccessed(LocalDateTime.now());
            progress.setUpdatedAt(LocalDateTime.now());
            progressRepository.save(progress);
        });
    }

    private void initializeProgress(String userId, String courseId) {
        if (progressRepository.findByUserIdAndCourseId(userId, courseId).isEmpty()) {
            UserCourseProgress progress = new UserCourseProgress();
            progress.setUserId(userId);
            progress.setCourseId(courseId);
            progress.setProgress(0);
            progress.setTotalLectures(0);
            progress.setCompletedLectures(0);
            progress.setEnrolledAt(LocalDateTime.now());
            progress.setLastAccessed(LocalDateTime.now());
            progress.setCreatedAt(LocalDateTime.now());
            progress.setUpdatedAt(LocalDateTime.now());
            progressRepository.save(progress);
        }
    }
}
