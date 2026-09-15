package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CourseOutlineModuleDTO;
import com.prwatech.skillama.dto.CourseShareMetadataDTO;
import com.prwatech.skillama.dto.GeneratedCourseDetailDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy View Details copy: generate once from the curriculum outline, store on
 * {@link Course}, regenerate only when the outline hash changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseDetailContentService {

    private final CourseService courseService;
    private final SkillamaAiClient skillamaAiClient;
    private final UserService userService;
    private final ConcurrentHashMap<String, Object> generationLocks = new ConcurrentHashMap<>();

    public Optional<CourseShareMetadataDTO> getShareMetadata(String courseId, String publicAppUrl) {
        if (courseService.findActiveById(courseId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(getOrRefresh(courseId, publicAppUrl, false, null));
    }

    /**
     * Admin force-refresh: generate, persist, return the draft. Concurrent View Details
     * callers wait on the same course lock.
     */
    public GeneratedCourseDetailDTO generateAndSave(String adminUserId, String courseId) {
        Course course = courseService.findActiveById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        User admin = userService.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        getOrRefresh(courseId, null, true, admin);
        Course saved = courseService.findActiveById(courseId).orElse(course);
        return GeneratedCourseDetailDTO.builder()
                .tagline(saved.getDetailTagline())
                .overview(saved.getDetailOverview())
                .description(saved.getDetailOverview())
                .objectives(saved.getDetailObjectives())
                .highlights(saved.getDetailHighlights())
                .prerequisites(saved.getDetailPrerequisites())
                .outcomes(saved.getDetailOutcomes())
                .audience(saved.getDetailAudience())
                .aiTutorHelp(saved.getDetailAiTutorHelp())
                .build();
    }

    CourseShareMetadataDTO getOrRefresh(String courseId, String publicAppUrl, boolean force, User billedUser) {
        Object lock = generationLocks.computeIfAbsent(courseId, key -> new Object());
        synchronized (lock) {
            Course course = courseService.findActiveById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
            List<CourseCurriculum> curriculum =
                    courseService.getCurriculumByCourseIdOrdered(courseId, false, false);
            List<CourseOutlineModuleDTO> outline = buildOutline(curriculum);
            String hash = curriculumHash(outline);

            if (!force && hash.equals(course.getDetailCurriculumHash()) && hasValidStoredDetail(course)) {
                return toShareDto(course, outline, publicAppUrl);
            }
            if (outline.isEmpty()) {
                return toShareDto(course, outline, publicAppUrl);
            }

            try {
                GeneratedCourseDetailDTO draft = skillamaAiClient.generateCourseDetail(
                        billedUser,
                        courseId,
                        course.getName(),
                        course.getDescription(),
                        outline);
                Course saved = courseService.saveAiGeneratedDetail(courseId, draft, hash);
                if (saved != null) {
                    course = saved;
                }
            } catch (RuntimeException e) {
                log.warn("Course detail generation failed for {}; returning stored copy if any", courseId, e);
                if (!hasValidStoredDetail(course)) {
                    // First request failed: still return catalog + outline, no throw.
                    return toShareDto(course, outline, publicAppUrl);
                }
            }
            return toShareDto(course, outline, publicAppUrl);
        }
    }

    static boolean hasValidStoredDetail(Course course) {
        return course != null
                && StringUtils.hasText(course.getDetailOverview())
                && course.getDetailOutcomes() != null
                && !course.getDetailOutcomes().isEmpty();
    }

    public static String curriculumHash(List<CourseOutlineModuleDTO> outline) {
        StringBuilder canonical = new StringBuilder();
        if (outline != null) {
            for (CourseOutlineModuleDTO module : outline) {
                canonical.append(module.getModuleName() == null ? "" : module.getModuleName().trim())
                        .append('\n');
                List<String> lectures = module.getLectures() == null ? List.of() : module.getLectures();
                for (String lecture : lectures) {
                    canonical.append('\t').append(lecture).append('\n');
                }
            }
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static List<CourseOutlineModuleDTO> buildOutline(List<CourseCurriculum> curriculum) {
        if (curriculum == null || curriculum.isEmpty()) {
            return List.of();
        }
        List<CourseOutlineModuleDTO> modules = new ArrayList<>();
        for (CourseCurriculum module : curriculum) {
            List<String> lectures = new ArrayList<>();
            if (module.getSubmodules() != null) {
                for (CourseCurriculum.Submodule submodule : module.getSubmodules()) {
                    if (submodule == null) {
                        continue;
                    }
                    if (!CourseService.isSubmoduleEnabled(submodule)) {
                        continue;
                    }
                    if (!StringUtils.hasText(submodule.getLabel())) {
                        continue;
                    }
                    lectures.add(submodule.getLabel().trim());
                }
            }
            if (lectures.isEmpty() && !StringUtils.hasText(module.getModuleName())) {
                continue;
            }
            modules.add(CourseOutlineModuleDTO.builder()
                    .moduleName(StringUtils.hasText(module.getModuleName())
                            ? module.getModuleName().trim()
                            : "Module")
                    .lectures(lectures)
                    .build());
        }
        return modules;
    }

    static CourseShareMetadataDTO toShareDto(
            Course course, List<CourseOutlineModuleDTO> outline, String publicAppUrl) {
        String base = publicAppUrl != null ? publicAppUrl.replaceAll("/$", "") : "https://skillama.co.in";
        String overview = StringUtils.hasText(course.getDetailOverview())
                ? course.getDetailOverview()
                : course.getDescription();
        return CourseShareMetadataDTO.builder()
                .courseId(course.getId())
                .title(course.getName())
                .description(overview)
                .imageUrl(course.getThumbnail())
                .shareUrl(base + "/courses/" + course.getId())
                .tagline(course.getDetailTagline())
                .overview(course.getDetailOverview())
                .objectives(course.getDetailObjectives())
                .highlights(course.getDetailHighlights())
                .prerequisites(course.getDetailPrerequisites())
                .outcomes(course.getDetailOutcomes())
                .audience(course.getDetailAudience())
                .aiTutorHelp(course.getDetailAiTutorHelp())
                .modules(outline)
                .build();
    }
}
