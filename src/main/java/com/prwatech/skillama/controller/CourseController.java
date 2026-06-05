package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.CourseShareMetadataDTO;
import com.prwatech.skillama.dto.StudyMaterialDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.service.CourseStudyMaterialService;
import com.prwatech.skillama.service.UserCourseAccessService;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/skillama/courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService courseService;
    private final CourseStudyMaterialService studyMaterialService;
    private final JwtUtils jwtUtils;
    private final UserService userService;
    private final UserCourseAccessService userCourseAccessService;

    @Value("${skillama.app.public-url:https://skillama.co.in}")
    private String publicAppUrl;

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
    public ResponseEntity<Course> getById(
            @PathVariable String id,
            HttpServletRequest request) {
        return courseService.findActiveById(id)
                .map(course -> {
                    enforceLearnerCourseAccess(request, id);
                    return ResponseEntity.ok(course);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{courseId}/curriculum")
    public ResponseEntity<List<CourseCurriculum>> getCourseCurriculum(
            @PathVariable String courseId,
            @RequestParam(value = "guest", required = false, defaultValue = "false") boolean guestAccess,
            @RequestParam(value = "forAdmin", required = false, defaultValue = "false") boolean forAdmin,
            HttpServletRequest request) {
        boolean adminView = forAdmin && isAdminUser(request);
        if (!guestAccess && !adminView) {
            enforceLearnerCourseAccess(request, courseId);
            String userId = extractUserId(request);
            if (userId != null) {
                userCourseAccessService.touchLastAccessed(userId, courseId);
            }
        }
        return ResponseEntity.ok(courseService.getCurriculumByCourseIdOrdered(courseId, guestAccess, adminView));
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

    /**
     * Public share metadata for social previews (OG tags). No auth required.
     */
    @GetMapping("/{id}/share")
    public ResponseEntity<CourseShareMetadataDTO> getShareMetadata(@PathVariable String id) {
        return courseService.findActiveById(id)
                .map(course -> {
                    String base = publicAppUrl != null ? publicAppUrl.replaceAll("/$", "") : "https://skillama.co.in";
                    String shareUrl = base + "/courses/" + id;
                    String imageUrl = StringUtils.hasText(course.getThumbnail())
                            ? course.getThumbnail()
                            : base + "/assets/images/aitutor.png";
                    return ResponseEntity.ok(CourseShareMetadataDTO.builder()
                            .courseId(course.getId())
                            .title(course.getName())
                            .description(course.getDescription())
                            .imageUrl(imageUrl)
                            .shareUrl(shareUrl)
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Downloadable study materials for enrolled learners (or admins).
     */
    @GetMapping("/{courseId}/materials")
    public ResponseEntity<List<StudyMaterialDTO>> getStudyMaterials(
            @PathVariable String courseId,
            HttpServletRequest request) {
        if (extractUserId(request) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        enforceLearnerCourseAccess(request, courseId);
        return ResponseEntity.ok(studyMaterialService.listForCourse(courseId));
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
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        String userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!userCourseAccessService.isOwner(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        courseService.softDelete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private void enforceLearnerCourseAccess(HttpServletRequest request, String courseId) {
        String userId = extractUserId(request);
        if (userId == null) {
            return;
        }
        if (userCourseAccessService.isAdminOrOwner(userId)) {
            return;
        }
        if (!userCourseAccessService.hasActiveEnrollment(userId, courseId)) {
            throw new ForbiddenException("You do not have access to this course.");
        }
    }

    private String extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String email = jwtUtils.extractUsername(authHeader.substring(7));
            return userService.findByEmail(email).map(User::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAdminUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        try {
            String email = jwtUtils.extractUsername(authHeader.substring(7));
            return userService.findByEmail(email)
                    .map(user -> user.getRole() == User.UserRole.ADMIN || user.getRole() == User.UserRole.OWNER)
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }
}
