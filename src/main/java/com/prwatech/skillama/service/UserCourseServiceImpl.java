package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CourseProgressDTO;
import com.prwatech.skillama.dto.LectureProgressDTO;
import com.prwatech.skillama.dto.UserCourseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.UserCourseEnrollment;
import com.prwatech.skillama.model.UserCourseProgress;
import com.prwatech.skillama.model.UserLectureProgress;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.UserCourseEnrollmentRepository;
import com.prwatech.skillama.repository.UserCourseProgressRepository;
import com.prwatech.skillama.repository.UserLectureProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserCourseServiceImpl implements UserCourseService {
    
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserCourseProgressRepository progressRepository;
    private final UserLectureProgressRepository lectureProgressRepository;
    private final CourseRepository courseRepository;
    private final CourseCurriculumRepository curriculumRepository;
    
    @Override
    public List<UserCourseDTO> getUserCoursesWithProgress(String userId) {
        // 1. Get all enrolled courses for the user
        List<UserCourseEnrollment> enrollments = enrollmentRepository.findByUserIdAndStatus(
            userId, UserCourseEnrollment.EnrollmentStatus.ACTIVE);
        
        // 2. For each enrollment, get course details and progress
        return enrollments.stream().map(enrollment -> {
            Course course = courseRepository.findById(enrollment.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
            
            // Get or create progress
            UserCourseProgress progress = progressRepository
                .findByUserIdAndCourseId(userId, enrollment.getCourseId())
                .orElseGet(() -> initializeProgress(userId, enrollment.getCourseId()));
            
            // Calculate total lectures from curriculum
            List<CourseCurriculum> curriculum = curriculumRepository.findByCourseIdOrderByOrderAsc(course.getId());
            int totalLectures = calculateTotalLectures(curriculum);
            
            // Calculate completed lectures
            long completedLectures = lectureProgressRepository
                .countByUserIdAndCourseIdAndCompleted(userId, course.getId(), true);
            
            // Calculate progress percentage
            int progressPercentage = totalLectures > 0 
                ? (int) Math.round((completedLectures * 100.0) / totalLectures) 
                : 0;
            
            // Determine status
            String status = determineStatus(progressPercentage);
            
            // Build DTO
            UserCourseDTO dto = new UserCourseDTO();
            dto.setId(course.getId());
            dto.setName(course.getName());
            dto.setDescription(course.getDescription());
            dto.setThumbnail(course.getThumbnail()); // Optional field
            dto.setProgress(progressPercentage);
            dto.setTotalLectures(totalLectures);
            dto.setCompletedLectures((int) completedLectures);
            dto.setStatus(status);
            dto.setEnrolledAt(enrollment.getEnrolledAt());
            dto.setLastAccessed(progress.getLastAccessed());
            
            return dto;
        }).collect(Collectors.toList());
    }
    
    @Override
    public CourseProgressDTO getCourseProgress(String userId, String courseId) {
        // Verify course exists
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        
        // Get or create progress
        UserCourseProgress progress = progressRepository
            .findByUserIdAndCourseId(userId, courseId)
            .orElseGet(() -> initializeProgress(userId, courseId));
        
        // Get curriculum
        List<CourseCurriculum> curriculum = curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
        int totalLectures = calculateTotalLectures(curriculum);
        
        // Get completed lectures count
        long completedLectures = lectureProgressRepository
            .countByUserIdAndCourseIdAndCompleted(userId, courseId, true);
        
        // Calculate progress percentage
        int progressPercentage = totalLectures > 0 
            ? (int) Math.round((completedLectures * 100.0) / totalLectures) 
            : 0;
        
        // Get all lecture progress for this course
        List<UserLectureProgress> lectureProgressList = lectureProgressRepository
            .findByUserIdAndCourseId(userId, courseId);
        
        // Build lecture progress DTOs
        List<LectureProgressDTO> lectureDTOs = lectureProgressList.stream()
            .map(lp -> {
                LectureProgressDTO dto = new LectureProgressDTO();
                dto.setLectureId(lp.getLectureId());
                dto.setModuleName(lp.getModuleName());
                dto.setLectureName(lp.getLectureName());
                dto.setCompleted(lp.getCompleted());
                dto.setCompletedAt(lp.getCompletedAt());
                dto.setTimeSpent(lp.getTimeSpent());
                return dto;
            })
            .collect(Collectors.toList());
        
        // Build response DTO
        CourseProgressDTO dto = new CourseProgressDTO();
        dto.setCourseId(courseId);
        dto.setProgress(progressPercentage);
        dto.setTotalLectures(totalLectures);
        dto.setCompletedLectures((int) completedLectures);
        dto.setLastAccessed(progress.getLastAccessed());
        dto.setLectures(lectureDTOs);
        
        return dto;
    }
    
    @Override
    @Transactional
    public CourseProgressDTO updateProgress(String userId, String courseId, String lectureId, 
                                           boolean completed, Integer timeSpent) {
        // Verify course exists
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        
        // Get curriculum to find lecture details
        List<CourseCurriculum> curriculum = curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
        
        // Find the lecture in curriculum to get module and lecture names
        String moduleName = null;
        String lectureName = null;
        for (CourseCurriculum module : curriculum) {
            if (module.getSubmodules() != null) {
                for (CourseCurriculum.Submodule submodule : module.getSubmodules()) {
                    if (lectureId.equals(submodule.getLabel())) {
                        moduleName = module.getModuleName();
                        lectureName = submodule.getLabel();
                        break;
                    }
                }
            }
        }
        
        // 1. Update or create UserLectureProgress
        UserLectureProgress lectureProgress = lectureProgressRepository
            .findByUserIdAndCourseIdAndLectureId(userId, courseId, lectureId)
            .orElse(new UserLectureProgress());
        
        lectureProgress.setUserId(userId);
        lectureProgress.setCourseId(courseId);
        lectureProgress.setLectureId(lectureId);
        lectureProgress.setModuleName(moduleName);
        lectureProgress.setLectureName(lectureName);
        lectureProgress.setCompleted(completed);
        if (completed && lectureProgress.getCompletedAt() == null) {
            lectureProgress.setCompletedAt(LocalDateTime.now());
        }
        if (timeSpent != null) {
            lectureProgress.setTimeSpent(timeSpent);
        }
        if (lectureProgress.getCreatedAt() == null) {
            lectureProgress.setCreatedAt(LocalDateTime.now());
        }
        lectureProgress.setUpdatedAt(LocalDateTime.now());
        lectureProgressRepository.save(lectureProgress);
        
        // 2. Recalculate and update UserCourseProgress
        updateCourseProgressAggregate(userId, courseId);
        
        // 3. Return updated progress
        CourseProgressDTO result = getCourseProgress(userId, courseId);
        result.setMessage("Progress updated successfully");
        return result;
    }
    
    private UserCourseProgress initializeProgress(String userId, String courseId) {
        UserCourseProgress progress = new UserCourseProgress();
        progress.setUserId(userId);
        progress.setCourseId(courseId);
        progress.setProgress(0);
        progress.setTotalLectures(0);
        progress.setCompletedLectures(0);
        progress.setLastAccessed(LocalDateTime.now());
        progress.setCreatedAt(LocalDateTime.now());
        progress.setUpdatedAt(LocalDateTime.now());
        return progressRepository.save(progress);
    }
    
    private void updateCourseProgressAggregate(String userId, String courseId) {
        // Get total lectures
        List<CourseCurriculum> curriculum = curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
        int totalLectures = calculateTotalLectures(curriculum);
        
        // Get completed lectures
        long completedLectures = lectureProgressRepository
            .countByUserIdAndCourseIdAndCompleted(userId, courseId, true);
        
        // Calculate progress
        int progress = totalLectures > 0 
            ? (int) Math.round((completedLectures * 100.0) / totalLectures) 
            : 0;
        
        // Update or create UserCourseProgress
        UserCourseProgress courseProgress = progressRepository
            .findByUserIdAndCourseId(userId, courseId)
            .orElse(new UserCourseProgress());
        
        courseProgress.setUserId(userId);
        courseProgress.setCourseId(courseId);
        courseProgress.setProgress(progress);
        courseProgress.setTotalLectures(totalLectures);
        courseProgress.setCompletedLectures((int) completedLectures);
        courseProgress.setLastAccessed(LocalDateTime.now());
        
        if (courseProgress.getCreatedAt() == null) {
            courseProgress.setCreatedAt(LocalDateTime.now());
        }
        courseProgress.setUpdatedAt(LocalDateTime.now());
        
        progressRepository.save(courseProgress);
    }
    
    private int calculateTotalLectures(List<CourseCurriculum> curriculum) {
        return CourseService.countEnabledLectures(curriculum);
    }
    
    private String determineStatus(int progress) {
        if (progress == 0) return "not-started";
        if (progress == 100) return "completed";
        return "in-progress";
    }
    
    @Override
    @Transactional
    public UserCourseEnrollment enrollUserToCourse(String userId, String courseId, 
                                                    UserCourseEnrollment.EnrollmentType enrollmentType) {
        // Verify course exists
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        
        // Check if already enrolled
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            // Update existing enrollment to ACTIVE if it was INACTIVE
            UserCourseEnrollment existing = enrollmentRepository
                .findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found"));
            
            if (existing.getStatus() == UserCourseEnrollment.EnrollmentStatus.INACTIVE) {
                existing.setStatus(UserCourseEnrollment.EnrollmentStatus.ACTIVE);
                existing.setEnrolledAt(LocalDateTime.now());
                return enrollmentRepository.save(existing);
            }
            // Already enrolled and active, return existing
            return existing;
        }
        
        // Create new enrollment
        UserCourseEnrollment enrollment = UserCourseEnrollment.builder()
            .userId(userId)
            .courseId(courseId)
            .enrollmentType(enrollmentType != null ? enrollmentType : UserCourseEnrollment.EnrollmentType.ASSIGNED)
            .enrolledAt(LocalDateTime.now())
            .status(UserCourseEnrollment.EnrollmentStatus.ACTIVE)
            .build();
        
        enrollment = enrollmentRepository.save(enrollment);
        
        // Initialize progress for the user-course combination
        initializeProgress(userId, courseId);
        
        return enrollment;
    }
    
    @Override
    @Transactional
    public void unenrollUserFromCourse(String userId, String courseId) {
        UserCourseEnrollment enrollment = enrollmentRepository
            .findByUserIdAndCourseId(userId, courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found"));
        
        // Set status to INACTIVE instead of deleting (for history)
        enrollment.setStatus(UserCourseEnrollment.EnrollmentStatus.INACTIVE);
        enrollmentRepository.save(enrollment);
    }
    
    @Override
    public boolean isUserEnrolled(String userId, String courseId) {
        return enrollmentRepository.existsByUserIdAndCourseId(userId, courseId) &&
               enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                   .map(e -> e.getStatus() == UserCourseEnrollment.EnrollmentStatus.ACTIVE)
                   .orElse(false);
    }
    
    @Override
    public List<UserCourseEnrollment> getUserEnrollments(String userId) {
        return enrollmentRepository.findByUserIdAndStatus(
            userId, UserCourseEnrollment.EnrollmentStatus.ACTIVE);
    }
}

