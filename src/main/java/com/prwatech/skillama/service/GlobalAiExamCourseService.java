package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.GlobalAiExamCourseDTO;
import com.prwatech.skillama.dto.GlobalAiExamCourseRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.GlobalAiExamCourse;
import com.prwatech.skillama.repository.GlobalAiExamCourseRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GlobalAiExamCourseService {

    public static final String DUPLICATE_MESSAGE = "This course is already enabled for AI Examination.";
    public static final String NOT_ENABLED_MESSAGE = "This course is not enabled for AI Examination.";

    private final GlobalAiExamCourseRepository repository;
    private final CourseService courseService;

    public static boolean isCustomCourseId(String courseId) {
        return StringUtils.hasText(courseId)
                && courseId.startsWith(GlobalAiExamCourse.CUSTOM_ID_PREFIX);
    }

    public boolean isEnabled(String courseId) {
        if (!StringUtils.hasText(courseId) || !repository.existsByCourseId(courseId)) {
            return false;
        }
        if (isCustomCourseId(courseId)) {
            return true;
        }
        return courseService.findActiveById(courseId)
                .filter(CourseService::isAvailableToLearner)
                .isPresent();
    }

    public String resolveDisplayName(String courseId) {
        if (!StringUtils.hasText(courseId)) {
            return "this course";
        }
        GlobalAiExamCourse row = repository.findByCourseId(courseId).orElse(null);
        if (row != null && StringUtils.hasText(row.getName())) {
            return row.getName();
        }
        return courseService.findById(courseId)
                .map(Course::getName)
                .filter(StringUtils::hasText)
                .orElse(row != null && StringUtils.hasText(row.getCourseId()) ? row.getCourseId() : "this course");
    }

    public List<GlobalAiExamCourseDTO> listAll() {
        return repository.findAll().stream()
                .map(this::toDto)
                .sorted(Comparator
                        .comparing(GlobalAiExamCourseDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(dto -> dto.getName() == null ? "" : dto.getName()))
                .collect(Collectors.toList());
    }

    public GlobalAiExamCourseDTO create(GlobalAiExamCourseRequestDTO request, String actorId) {
        ResolvedSubject subject = resolveNewSubject(request);
        assertUnique(subject.courseId(), subject.nameKey(), null);

        GlobalAiExamCourse row = GlobalAiExamCourse.builder()
                .courseId(subject.courseId())
                .name(subject.name())
                .description(subject.description())
                .nameKey(subject.nameKey())
                .createdAt(IndiaTime.now())
                .createdBy(actorId)
                .updatedAt(IndiaTime.now())
                .updatedBy(actorId)
                .build();
        return toDto(saveUnique(row));
    }

    public GlobalAiExamCourseDTO update(String id, GlobalAiExamCourseRequestDTO request, String actorId) {
        GlobalAiExamCourse row = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Global AI Examination course not found"));
        ResolvedSubject subject = resolveUpdatedSubject(row, request);
        assertUnique(subject.courseId(), subject.nameKey(), row.getId());

        row.setCourseId(subject.courseId());
        row.setName(subject.name());
        row.setDescription(subject.description());
        row.setNameKey(subject.nameKey());
        row.setUpdatedAt(IndiaTime.now());
        row.setUpdatedBy(actorId);
        return toDto(saveUnique(row));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Global AI Examination course not found");
        }
        repository.deleteById(id);
    }

    private ResolvedSubject resolveNewSubject(GlobalAiExamCourseRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("courseId or name is required");
        }
        if (StringUtils.hasText(request.getCourseId())) {
            return fromCatalogCourse(request.getCourseId().trim(), request.getDescription());
        }
        if (StringUtils.hasText(request.getName())) {
            return fromCustomName(request.getName(), request.getDescription());
        }
        throw new IllegalArgumentException("courseId or name is required");
    }

    private ResolvedSubject resolveUpdatedSubject(GlobalAiExamCourse row, GlobalAiExamCourseRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("courseId or name is required");
        }
        if (StringUtils.hasText(request.getCourseId())) {
            return fromCatalogCourse(request.getCourseId().trim(),
                    firstNonBlank(request.getDescription(), row.getDescription()));
        }
        if (StringUtils.hasText(request.getName())) {
            String description = request.getDescription() != null
                    ? trimToNull(request.getDescription())
                    : row.getDescription();
            if (isCustomCourseId(row.getCourseId())) {
                return new ResolvedSubject(row.getCourseId(), request.getName().trim(),
                        description, nameKey(request.getName()));
            }
            return fromCustomName(request.getName(), description);
        }
        throw new IllegalArgumentException("courseId or name is required");
    }

    private ResolvedSubject fromCatalogCourse(String courseId, String description) {
        Course course = courseService.findActiveById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        if (!CourseService.isAvailableToLearner(course)) {
            throw new IllegalArgumentException("This course is not currently available.");
        }
        String name = StringUtils.hasText(course.getName()) ? course.getName().trim() : course.getId();
        return new ResolvedSubject(
                course.getId(),
                name,
                firstNonBlank(description, course.getDescription()),
                nameKey(name));
    }

    private ResolvedSubject fromCustomName(String rawName, String description) {
        String name = rawName.trim();
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("name is required");
        }
        if (name.length() > 120) {
            throw new IllegalArgumentException("name must be 120 characters or fewer");
        }
        return new ResolvedSubject(customCourseId(name), name, trimToNull(description), nameKey(name));
    }

    private void assertUnique(String courseId, String nameKey, String excludeId) {
        repository.findByCourseId(courseId).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw new IllegalStateException(DUPLICATE_MESSAGE);
            }
        });
        if (StringUtils.hasText(nameKey)) {
            repository.findByNameKey(nameKey).ifPresent(existing -> {
                if (excludeId == null || !excludeId.equals(existing.getId())) {
                    throw new IllegalStateException(DUPLICATE_MESSAGE);
                }
            });
        }
    }

    private GlobalAiExamCourse saveUnique(GlobalAiExamCourse row) {
        try {
            return repository.save(row);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(DUPLICATE_MESSAGE, e);
        }
    }

    private GlobalAiExamCourseDTO toDto(GlobalAiExamCourse row) {
        boolean custom = isCustomCourseId(row.getCourseId());
        Course course = custom ? null : courseService.findById(row.getCourseId()).orElse(null);
        boolean available = custom || CourseService.isAvailableToLearner(course);
        String name = StringUtils.hasText(row.getName())
                ? row.getName()
                : (course != null ? course.getName() : null);
        String description = StringUtils.hasText(row.getDescription())
                ? row.getDescription()
                : (course != null ? course.getDescription() : null);
        return GlobalAiExamCourseDTO.builder()
                .id(row.getId())
                .courseId(row.getCourseId())
                .name(name)
                .thumbnail(course != null ? course.getThumbnail() : null)
                .description(description)
                .available(available)
                .catalogLinked(!custom && course != null)
                .createdAt(row.getCreatedAt())
                .createdBy(row.getCreatedBy())
                .updatedAt(row.getUpdatedAt())
                .updatedBy(row.getUpdatedBy())
                .build();
    }

    static String nameKey(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static String customCourseId(String name) {
        String slug = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(slug)) {
            slug = "subject";
        }
        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-+$", "");
        }
        return GlobalAiExamCourse.CUSTOM_ID_PREFIX + slug;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return trimToNull(fallback);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ResolvedSubject(String courseId, String name, String description, String nameKey) {}
}
