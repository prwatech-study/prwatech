package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.GlobalAiExamCourseRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.GlobalAiExamCourse;
import com.prwatech.skillama.repository.GlobalAiExamCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalAiExamCourseServiceTest {

    @Mock private GlobalAiExamCourseRepository repository;
    @Mock private CourseService courseService;

    private GlobalAiExamCourseService service;

    @BeforeEach
    void setUp() {
        service = new GlobalAiExamCourseService(repository, courseService);
    }

    private Course python() {
        return Course.builder().id("c1").name("Python").active(true).build();
    }

    private GlobalAiExamCourseRequestDTO courseRequest(String courseId) {
        GlobalAiExamCourseRequestDTO dto = new GlobalAiExamCourseRequestDTO();
        dto.setCourseId(courseId);
        return dto;
    }

    private GlobalAiExamCourseRequestDTO nameRequest(String name) {
        GlobalAiExamCourseRequestDTO dto = new GlobalAiExamCourseRequestDTO();
        dto.setName(name);
        return dto;
    }

    @Test
    void isEnabledRequiresConfigAndLearnerAvailableCourse() {
        when(repository.existsByCourseId("c1")).thenReturn(true);
        when(courseService.findActiveById("c1")).thenReturn(Optional.of(python()));
        assertTrue(service.isEnabled("c1"));

        when(repository.existsByCourseId("c2")).thenReturn(false);
        assertFalse(service.isEnabled("c2"));
    }

    @Test
    void isEnabledAllowsCustomSubjectWithoutCatalogCourse() {
        when(repository.existsByCourseId("custom-system-design")).thenReturn(true);
        assertTrue(service.isEnabled("custom-system-design"));
        verify(courseService, never()).findActiveById(any());
    }

    @Test
    void createRejectsDuplicateCourse() {
        when(courseService.findActiveById("c1")).thenReturn(Optional.of(python()));
        when(repository.findByCourseId("c1")).thenReturn(Optional.of(
                GlobalAiExamCourse.builder().id("other").courseId("c1").build()));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.create(courseRequest("c1"), "admin-1"));
        assertEquals(GlobalAiExamCourseService.DUPLICATE_MESSAGE, ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createCustomSubjectWithoutCatalogCourse() {
        when(repository.findByCourseId("custom-system-design")).thenReturn(Optional.empty());
        when(repository.findByNameKey("system design")).thenReturn(Optional.empty());
        when(repository.save(any(GlobalAiExamCourse.class))).thenAnswer(inv -> {
            GlobalAiExamCourse row = inv.getArgument(0);
            row.setId("cfg-custom");
            return row;
        });

        var dto = service.create(nameRequest("System Design"), "admin-1");
        assertEquals("cfg-custom", dto.getId());
        assertEquals("custom-system-design", dto.getCourseId());
        assertEquals("System Design", dto.getName());
        assertTrue(dto.isAvailable());
        assertFalse(dto.isCatalogLinked());

        ArgumentCaptor<GlobalAiExamCourse> captor = ArgumentCaptor.forClass(GlobalAiExamCourse.class);
        verify(repository).save(captor.capture());
        assertEquals("custom-system-design", captor.getValue().getCourseId());
        assertEquals("system design", captor.getValue().getNameKey());
        verify(courseService, never()).findActiveById(any());
    }

    @Test
    void createRejectsDuplicateCustomName() {
        when(repository.findByCourseId("custom-system-design")).thenReturn(Optional.empty());
        when(repository.findByNameKey("system design")).thenReturn(Optional.of(
                GlobalAiExamCourse.builder().id("cfg-1").courseId("custom-system-design").nameKey("system design").build()));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.create(nameRequest("System Design"), "admin-1"));
        assertEquals(GlobalAiExamCourseService.DUPLICATE_MESSAGE, ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createMapsConcurrentDuplicateKeyToValidationMessage() {
        when(courseService.findActiveById("c1")).thenReturn(Optional.of(python()));
        when(repository.findByCourseId("c1")).thenReturn(Optional.empty());
        when(repository.findByNameKey("python")).thenReturn(Optional.empty());
        when(repository.save(any(GlobalAiExamCourse.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.create(courseRequest("c1"), "admin-1"));
        assertEquals(GlobalAiExamCourseService.DUPLICATE_MESSAGE, ex.getMessage());
    }

    @Test
    void createPersistsUniqueCourse() {
        when(courseService.findActiveById("c1")).thenReturn(Optional.of(python()));
        when(repository.findByCourseId("c1")).thenReturn(Optional.empty());
        when(repository.findByNameKey("python")).thenReturn(Optional.empty());
        when(repository.save(any(GlobalAiExamCourse.class))).thenAnswer(inv -> {
            GlobalAiExamCourse row = inv.getArgument(0);
            row.setId("cfg-1");
            return row;
        });
        when(courseService.findById("c1")).thenReturn(Optional.of(python()));

        var dto = service.create(courseRequest("c1"), "admin-1");
        assertEquals("cfg-1", dto.getId());
        assertEquals("c1", dto.getCourseId());
        assertEquals("Python", dto.getName());
        assertTrue(dto.isAvailable());
        assertTrue(dto.isCatalogLinked());

        ArgumentCaptor<GlobalAiExamCourse> captor = ArgumentCaptor.forClass(GlobalAiExamCourse.class);
        verify(repository).save(captor.capture());
        assertEquals("c1", captor.getValue().getCourseId());
        assertEquals("admin-1", captor.getValue().getCreatedBy());
    }

    @Test
    void deleteRemovesConfigOnly() {
        when(repository.existsById("cfg-1")).thenReturn(true);
        service.delete("cfg-1");
        verify(repository).deleteById("cfg-1");
    }

    @Test
    void deleteMissingRowThrowsNotFound() {
        when(repository.existsById("missing")).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.delete("missing"));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void listAllIncludesConfiguredCoursesWithoutEnrollment() {
        GlobalAiExamCourse row = GlobalAiExamCourse.builder()
                .id("cfg-1")
                .courseId("c1")
                .name("Python")
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
        when(repository.findAll()).thenReturn(List.of(row));
        when(courseService.findById("c1")).thenReturn(Optional.of(python()));

        var list = service.listAll();
        assertEquals(1, list.size());
        assertEquals("Python", list.get(0).getName());
        assertEquals("c1", list.get(0).getCourseId());
        assertTrue(list.get(0).isAvailable());
    }

    @Test
    void resolveDisplayNamePrefersStoredCustomName() {
        when(repository.findByCourseId("custom-ml")).thenReturn(Optional.of(
                GlobalAiExamCourse.builder().courseId("custom-ml").name("Machine Learning").build()));
        assertEquals("Machine Learning", service.resolveDisplayName("custom-ml"));
    }
}
