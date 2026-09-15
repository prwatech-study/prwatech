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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GlobalAiExamCourseService {

    public static final String DUPLICATE_MESSAGE = "This course is already enabled for AI Examination.";
    public static final String NOT_ENABLED_MESSAGE = "This course is not enabled for AI Examination.";

    private final GlobalAiExamCourseRepository repository;
    private final CourseService courseService;

    public boolean isEnabled(String courseId) {
        if (!StringUtils.hasText(courseId) || !repository.existsByCourseId(courseId)) {
            return false;
        }
        return courseService.findActiveById(courseId)
                .filter(CourseService::isAvailableToLearner)
                .isPresent();
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
        Course course = requireAvailableCourse(request);
        String courseId = course.getId();
        if (repository.existsByCourseId(courseId)) {
            throw new IllegalStateException(DUPLICATE_MESSAGE);
        }

        GlobalAiExamCourse row = GlobalAiExamCourse.builder()
                .courseId(courseId)
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
        Course course = requireAvailableCourse(request);
        String courseId = course.getId();

        if (!courseId.equals(row.getCourseId()) && repository.existsByCourseId(courseId)) {
            throw new IllegalStateException(DUPLICATE_MESSAGE);
        }

        row.setCourseId(courseId);
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

    private Course requireAvailableCourse(GlobalAiExamCourseRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getCourseId())) {
            throw new IllegalArgumentException("courseId is required");
        }
        String courseId = request.getCourseId().trim();
        Course course = courseService.findActiveById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        if (!CourseService.isAvailableToLearner(course)) {
            throw new IllegalArgumentException("This course is not currently available.");
        }
        return course;
    }

    private GlobalAiExamCourse saveUnique(GlobalAiExamCourse row) {
        try {
            return repository.save(row);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(DUPLICATE_MESSAGE, e);
        }
    }

    private GlobalAiExamCourseDTO toDto(GlobalAiExamCourse row) {
        Course course = courseService.findById(row.getCourseId()).orElse(null);
        boolean available = CourseService.isAvailableToLearner(course);
        return GlobalAiExamCourseDTO.builder()
                .id(row.getId())
                .courseId(row.getCourseId())
                .name(course != null ? course.getName() : null)
                .thumbnail(course != null ? course.getThumbnail() : null)
                .description(course != null ? course.getDescription() : null)
                .available(available)
                .createdAt(row.getCreatedAt())
                .createdBy(row.getCreatedBy())
                .updatedAt(row.getUpdatedAt())
                .updatedBy(row.getUpdatedBy())
                .build();
    }
}
