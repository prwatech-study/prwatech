package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.StudyMaterialDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseStudyMaterial;
import com.prwatech.skillama.repository.CourseStudyMaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseStudyMaterialServiceTest {

    @Mock private CourseStudyMaterialRepository repository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CourseService courseService;
    @Mock private MultipartFile file;

    private CourseStudyMaterialService service;

    @BeforeEach
    void setUp() {
        service = new CourseStudyMaterialService(repository, fileStorageService, courseService);
        when(courseService.findActiveById("c1")).thenReturn(Optional.of(Course.builder().id("c1").build()));
        when(repository.save(any(CourseStudyMaterial.class))).thenAnswer(inv -> {
            CourseStudyMaterial m = inv.getArgument(0);
            return m;
        });
    }

    @Test
    void listForMissingCourseThrows() {
        when(courseService.findActiveById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.listForCourse("ghost"));
    }

    @Test
    void listReturnsMaterialsForCourse() {
        when(repository.findByCourseIdOrderBySortOrderAscUploadedAtAsc("c1"))
                .thenReturn(List.of(CourseStudyMaterial.builder().id("m1").courseId("c1").title("Slides").build()));

        List<StudyMaterialDTO> dtos = service.listForCourse("c1");
        assertEquals(1, dtos.size());
        assertEquals("Slides", dtos.get(0).getTitle());
    }

    @Test
    void uploadToMissingCourseThrows() {
        when(courseService.findActiveById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.upload("ghost", file, "t", "d", 1, "admin"));
    }

    @Test
    void uploadStoresFileAndDerivesTitleAndS3Key() throws IOException {
        when(file.getOriginalFilename()).thenReturn("lecture-notes.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(2048L);
        when(fileStorageService.uploadDocumentToS3(any(), anyString()))
                .thenReturn("https://bucket.s3.amazonaws.com/courses/c1/materials/lecture-notes.pdf");
        when(repository.findByCourseIdOrderBySortOrderAscUploadedAtAsc("c1")).thenReturn(List.of());

        // title blank → falls back to filename; sortOrder null → derived
        StudyMaterialDTO dto = service.upload("c1", file, "  ", null, null, "admin1");

        assertEquals("lecture-notes.pdf", dto.getTitle());
        assertEquals("c1", dto.getCourseId());

        ArgumentCaptor<CourseStudyMaterial> captor = ArgumentCaptor.forClass(CourseStudyMaterial.class);
        verify(repository).save(captor.capture());
        CourseStudyMaterial saved = captor.getValue();
        assertEquals("courses/c1/materials/lecture-notes.pdf", saved.getS3Key());
        assertEquals(1, saved.getSortOrder()); // empty list → next = 1
        assertEquals("admin1", saved.getUploadedBy());
    }

    @Test
    void deleteMissingMaterialThrows() {
        when(repository.findById("mX")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete("c1", "mX"));
    }

    @Test
    void deleteRejectsMaterialFromDifferentCourse() {
        when(repository.findById("m1")).thenReturn(Optional.of(
                CourseStudyMaterial.builder().id("m1").courseId("OTHER").build()));
        assertThrows(ResourceNotFoundException.class, () -> service.delete("c1", "m1"));
    }

    @Test
    void deleteRemovesManagedFileAndEntity() throws IOException {
        CourseStudyMaterial material = CourseStudyMaterial.builder()
                .id("m1").courseId("c1").fileUrl("https://bucket.s3.amazonaws.com/x.pdf").build();
        when(repository.findById("m1")).thenReturn(Optional.of(material));
        when(fileStorageService.isManagedStorageUrl(material.getFileUrl())).thenReturn(true);

        service.delete("c1", "m1");

        verify(fileStorageService).deleteFile(material.getFileUrl());
        verify(repository).delete(material);
    }

    @Test
    void deleteSkipsFileDeletionForUnmanagedUrl() throws IOException {
        CourseStudyMaterial material = CourseStudyMaterial.builder()
                .id("m1").courseId("c1").fileUrl("https://external.example.com/x.pdf").build();
        when(repository.findById("m1")).thenReturn(Optional.of(material));
        when(fileStorageService.isManagedStorageUrl(material.getFileUrl())).thenReturn(false);

        service.delete("c1", "m1");

        verify(fileStorageService, never()).deleteFile(anyString());
        verify(repository).delete(material);
    }

    @Test
    void deleteSwallowsFileStorageIOException() throws IOException {
        CourseStudyMaterial material = CourseStudyMaterial.builder()
                .id("m1").courseId("c1").fileUrl("https://bucket.s3.amazonaws.com/x.pdf").build();
        when(repository.findById("m1")).thenReturn(Optional.of(material));
        when(fileStorageService.isManagedStorageUrl(material.getFileUrl())).thenReturn(true);
        org.mockito.Mockito.doThrow(new IOException("s3 down")).when(fileStorageService).deleteFile(anyString());

        service.delete("c1", "m1"); // must not propagate
        verify(repository).delete(material);
    }
}
