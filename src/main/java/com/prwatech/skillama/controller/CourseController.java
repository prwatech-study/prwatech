package com.prwatech.skillama.controller;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/skillama/courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService courseService;

    @PostMapping
    public ResponseEntity<Course> create(@RequestBody Course course) {
        return ResponseEntity.ok(courseService.create(course));
    }

    @GetMapping
    public ResponseEntity<Page<Course>> getAllCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order
    ) {
        boolean desc = order.equalsIgnoreCase("desc");
        return ResponseEntity.ok(courseService.findAll(page, size, sortBy, desc));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Course> getById(@PathVariable String id) {
        return courseService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{courseId}/curriculum")
    public ResponseEntity<List<CourseCurriculum>> getCourseCurriculum(
            @PathVariable String courseId,
            @RequestParam(value = "guest", required = false, defaultValue = "false") boolean guestAccess) {
        // Returns full curriculum for all users (including guests)
        // Access control (which lectures are unlocked) is handled by UserProfileService
        return ResponseEntity.ok(courseService.getCurriculumByCourseIdOrdered(courseId, guestAccess));
    }

    /**
     * Get guest/default course for non-logged-in users
     * This endpoint is public and does not require authentication
     * @throws NotFoundException if no guest course is configured
     */
    @GetMapping("/guest")
    public ResponseEntity<Course> getGuestCourse() {
        Course guestCourse = courseService.getGuestCourseOrThrow();
        return ResponseEntity.ok(guestCourse);
    }

    /**
     * Get guest course curriculum (full curriculum - all modules visible but only first lecture unlocked)
     * This endpoint is public and does not require authentication
     * Note: Guest users see the full course structure but only the first lecture is accessible.
     * This creates a "teaser" effect to encourage users to sign up.
     * @throws NotFoundException if guest course not found or curriculum is empty
     */
    @GetMapping("/guest/curriculum")
    public ResponseEntity<List<CourseCurriculum>> getGuestCourseCurriculum() {
        List<CourseCurriculum> curriculum = courseService.getGuestCourseCurriculum();
        return ResponseEntity.ok(curriculum);
    }

    /**
     * Get all public courses accessible to non-logged-in users
     * This endpoint is public and does not require authentication
     */
    @GetMapping("/public")
    public ResponseEntity<List<Course>> getPublicCourses() {
        return ResponseEntity.ok(courseService.findPublicCourses());
    }

    /**
     * Get first public course (default for guest access)
     * This endpoint is public and does not require authentication
     */
    @GetMapping("/public/first")
    public ResponseEntity<Course> getFirstPublicCourse() {
        return courseService.findFirstPublicCourse()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Course> update(@PathVariable String id, @RequestBody Course course) {
        Course updated = courseService.update(id, course);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
