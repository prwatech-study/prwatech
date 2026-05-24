package com.prwatech.skillama.script;

import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.repository.CourseRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;
import java.util.List;

/**
 * Migration script to set up a default guest course
 * This can be run as a CommandLineRunner on application startup
 * Or can be disabled by commenting out @Component annotation
 * 
 * To run manually, call the /skillama/admin/courses/setup-guest-course endpoint
 */
@Component
@AllArgsConstructor
public class GuestCourseMigrationScript implements CommandLineRunner {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GuestCourseMigrationScript.class);
    
    private final CourseRepository courseRepository;
    
    // Set to false to disable automatic migration on startup
    private static final boolean AUTO_RUN_MIGRATION = false;
    
    @Override
    public void run(String... args) throws Exception {
        if (!AUTO_RUN_MIGRATION) {
            LOGGER.info("Guest course migration script is disabled. Use /skillama/admin/courses/setup-guest-course endpoint to run migration.");
            return;
        }
        
        LOGGER.info("Starting guest course migration...");
        setupGuestCourse();
        LOGGER.info("Guest course migration completed.");
    }
    
    /**
     * Sets up a default guest course if one doesn't exist
     * Strategy:
     * 1. Check if a course with isGuestCourse=true exists
     * 2. If not, check if any course with isPublic=true exists
     * 3. If not, use the first available course and mark it as guest course
     * 4. If no courses exist, create a default guest course
     */
    public void setupGuestCourse() {
        try {
            // Check if guest course already exists
            if (courseRepository.findByIsGuestCourseTrue().isPresent()) {
                LOGGER.info("Guest course already exists. Skipping migration.");
                return;
            }
            
            // Check if any public course exists
            List<Course> publicCourses = courseRepository.findByIsPublicTrue();
            if (!publicCourses.isEmpty()) {
                Course firstPublicCourse = publicCourses.get(0);
                firstPublicCourse.setIsGuestCourse(true);
                courseRepository.save(firstPublicCourse);
                LOGGER.info("Marked existing public course '{}' (ID: {}) as guest course.", 
                        firstPublicCourse.getName(), firstPublicCourse.getId());
                return;
            }
            
            // Check if any course exists at all
            List<Course> allCourses = courseRepository.findAll();
            if (!allCourses.isEmpty()) {
                Course firstCourse = allCourses.get(0);
                firstCourse.setIsGuestCourse(true);
                firstCourse.setIsPublic(true);
                courseRepository.save(firstCourse);
                LOGGER.info("Marked first available course '{}' (ID: {}) as guest course.", 
                        firstCourse.getName(), firstCourse.getId());
                return;
            }
            
            // No courses exist, create a default guest course
            Course defaultGuestCourse = Course.builder()
                    .name("Python Fundamentals - Guest Access")
                    .description("Introduction to Python programming for non-logged-in users. This is a demo course to showcase the LMS platform.")
                    .isGuestCourse(true)
                    .isPublic(true)
                    .createdAt(IndiaTime.now())
                    .updatedAt(IndiaTime.now())
                    .createdBy("system")
                    .updatedBy("system")
                    .build();
            
            Course savedCourse = courseRepository.save(defaultGuestCourse);
            LOGGER.info("Created default guest course '{}' (ID: {}). Please add curriculum modules to this course.", 
                    savedCourse.getName(), savedCourse.getId());
            
        } catch (Exception e) {
            LOGGER.error("Error during guest course migration", e);
            throw new RuntimeException("Guest course migration failed", e);
        }
    }
    
    /**
     * Sets a specific course as the guest course
     * @param courseId The ID of the course to set as guest course
     * @return true if successful, false if course not found
     */
    public boolean setCourseAsGuestCourse(String courseId) {
        try {
            return courseRepository.findById(courseId)
                    .map(course -> {
                        // Unset any existing guest course
                        courseRepository.findByIsGuestCourseTrue()
                                .ifPresent(existingGuest -> {
                                    existingGuest.setIsGuestCourse(false);
                                    courseRepository.save(existingGuest);
                                    LOGGER.info("Unset existing guest course '{}' (ID: {})", 
                                            existingGuest.getName(), existingGuest.getId());
                                });
                        
                        // Set new guest course
                        course.setIsGuestCourse(true);
                        course.setIsPublic(true); // Also make it public
                        course.setUpdatedAt(IndiaTime.now());
                        courseRepository.save(course);
                        LOGGER.info("Set course '{}' (ID: {}) as guest course.", 
                                course.getName(), course.getId());
                        return true;
                    })
                    .orElse(false);
        } catch (Exception e) {
            LOGGER.error("Error setting course as guest course: {}", courseId, e);
            return false;
        }
    }
}

