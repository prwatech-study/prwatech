package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.KnowledgeBaseSyncResponseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseKnowledgeFile;
import com.prwatech.skillama.model.CourseKnowledgeSyncEvent;
import com.prwatech.skillama.model.CourseKnowledgeSyncState;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseKnowledgeFileRepository;
import com.prwatech.skillama.repository.CourseKnowledgeSyncEventRepository;
import com.prwatech.skillama.repository.CourseKnowledgeSyncStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseKnowledgeFileServiceTest {

    @Mock private CourseKnowledgeFileRepository fileRepository;
    @Mock private CourseKnowledgeSyncEventRepository syncEventRepository;
    @Mock private CourseKnowledgeSyncStateRepository syncStateRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CourseService courseService;
    @Mock private UserService userService;
    @Mock private SkillamaAiClient skillamaAiClient;
    @Mock private MultipartFile file;

    private CourseKnowledgeFileService service;

    @BeforeEach
    void setUp() {
        service = new CourseKnowledgeFileService(
                fileRepository,
                syncEventRepository,
                syncStateRepository,
                fileStorageService,
                courseService,
                userService,
                skillamaAiClient);
        ReflectionTestUtils.setField(service, "knowledgeBasePrefix", "knowledge-base");
        ReflectionTestUtils.setField(service, "embedRatePerMtoken", 0.02);
        ReflectionTestUtils.setField(service, "ownerApprovalUsd", 2.0);

        when(courseService.findActiveById("c1")).thenReturn(Optional.of(Course.builder().id("c1").build()));
        when(fileRepository.save(any(CourseKnowledgeFile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(syncEventRepository.save(any(CourseKnowledgeSyncEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(syncStateRepository.save(any(CourseKnowledgeSyncState.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listForMissingCourseThrows() {
        when(courseService.findActiveById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.listForCourse("ghost"));
    }

    @Test
    void uploadStoresFileAndSidecarMetadataInS3() throws IOException {
        when(file.getOriginalFilename()).thenReturn("notes.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(4000L);

        service.upload("c1", file, null, "admin1");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).uploadKnowledgeBaseDocument(eq(file), eq("c1"), keyCaptor.capture());
        assertEquals("knowledge-base/c1/notes.pdf", keyCaptor.getValue());
    }

    @Test
    void uploadOverwritesExistingKeyWhenReplacing() throws IOException {
        CourseKnowledgeFile existing = CourseKnowledgeFile.builder()
                .id("f1")
                .courseId("c1")
                .s3Key("knowledge-base/c1/notes.pdf")
                .fileName("notes.pdf")
                .build();
        when(fileRepository.findByIdAndCourseIdAndDeletedAtIsNull("f1", "c1"))
                .thenReturn(Optional.of(existing));
        when(file.getOriginalFilename()).thenReturn("notes-v2.pdf");
        when(file.getSize()).thenReturn(8000L);

        service.upload("c1", file, "f1", "admin1");

        verify(fileStorageService).uploadKnowledgeBaseDocument(file, "c1", "knowledge-base/c1/notes.pdf");
    }

    @Test
    void deleteRemovesFileAndSidecarFromS3() throws IOException {
        CourseKnowledgeFile kbFile = CourseKnowledgeFile.builder()
                .id("f1").courseId("c1").s3Key("knowledge-base/c1/a.pdf").build();
        when(fileRepository.findByIdAndCourseIdAndDeletedAtIsNull("f1", "c1"))
                .thenReturn(Optional.of(kbFile));

        service.delete("c1", "f1");

        verify(fileStorageService).deleteKnowledgeBaseDocument("knowledge-base/c1/a.pdf");
        verify(fileRepository).save(kbFile);
    }

    @Test
    void syncEstimatesCostFromPendingTokensSinceLastSync() {
        when(syncStateRepository.findByCourseId("c1")).thenReturn(Optional.of(
                CourseKnowledgeSyncState.builder()
                        .courseId("c1")
                        .lastSyncedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                        .kbVersion(1L)
                        .build()));
        when(fileRepository.findByCourseIdAndDeletedAtIsNullOrderByUploadedAtDesc("c1"))
                .thenReturn(List.of(CourseKnowledgeFile.builder()
                        .courseId("c1")
                        .estimatedTokens(2_000_000L)
                        .uploadedAt(LocalDateTime.of(2026, 2, 1, 0, 0))
                        .build()));
        when(userService.findById("admin1")).thenReturn(Optional.of(
                User.builder().id("admin1").role(User.UserRole.ADMIN).build()));
        when(skillamaAiClient.startKnowledgeBaseSync("c1"))
                .thenReturn(Map.of("ingestionJobId", "job-1", "status", "STARTING"));

        KnowledgeBaseSyncResponseDTO result = service.sync("c1", "admin1");
        assertEquals(0.04, result.getEstimatedCostUsd(), 0.001);
    }

    @Test
    void syncRequiresOwnerApprovalWhenEstimatedCostExceedsThreshold() {
        when(syncStateRepository.findByCourseId("c1")).thenReturn(Optional.empty());
        when(fileRepository.findByCourseIdAndDeletedAtIsNullOrderByUploadedAtDesc("c1"))
                .thenReturn(List.of(CourseKnowledgeFile.builder()
                        .courseId("c1")
                        .estimatedTokens(200_000_000L)
                        .uploadedAt(LocalDateTime.now())
                        .build()));
        when(userService.findById("admin1")).thenReturn(Optional.of(
                User.builder().id("admin1").role(User.UserRole.ADMIN).build()));

        assertThrows(RuntimeException.class, () -> service.sync("c1", "admin1"));
    }

    @Test
    void syncAllowsAdminWhenUnderApprovalThreshold() {
        when(syncStateRepository.findByCourseId("c1")).thenReturn(Optional.empty());
        when(fileRepository.findByCourseIdAndDeletedAtIsNullOrderByUploadedAtDesc("c1"))
                .thenReturn(List.of());
        when(userService.findById("admin1")).thenReturn(Optional.of(
                User.builder().id("admin1").role(User.UserRole.ADMIN).build()));
        when(skillamaAiClient.startKnowledgeBaseSync("c1"))
                .thenReturn(Map.of("ingestionJobId", "job-2", "status", "STARTING"));

        KnowledgeBaseSyncResponseDTO result = service.sync("c1", "admin1");
        assertEquals("job-2", result.getIngestionJobId());
    }

    @Test
    void syncWritesStartedSyncHistoryEventBeforeCallingAiClient() {
        when(syncStateRepository.findByCourseId("c1")).thenReturn(Optional.empty());
        when(fileRepository.findByCourseIdAndDeletedAtIsNullOrderByUploadedAtDesc("c1"))
                .thenReturn(List.of());
        when(userService.findById("owner1")).thenReturn(Optional.of(
                User.builder().id("owner1").role(User.UserRole.OWNER).build()));
        when(skillamaAiClient.startKnowledgeBaseSync("c1"))
                .thenReturn(Map.of("ingestionJobId", "job-3", "status", "STARTING"));

        service.sync("c1", "owner1");

        ArgumentCaptor<CourseKnowledgeSyncEvent> captor = ArgumentCaptor.forClass(CourseKnowledgeSyncEvent.class);
        verify(syncEventRepository).save(captor.capture());
        assertEquals(CourseKnowledgeSyncEvent.Status.STARTED, captor.getValue().getStatus());
        assertEquals("job-3", captor.getValue().getIngestionJobId());
    }

    @Test
    void syncStatusUpdatesHistoryEventToCompleteOnSuccess() {
        CourseKnowledgeSyncEvent event = CourseKnowledgeSyncEvent.builder()
                .courseId("c1")
                .ingestionJobId("job-9")
                .status(CourseKnowledgeSyncEvent.Status.STARTED)
                .build();
        when(syncEventRepository.findByCourseIdAndIngestionJobId("c1", "job-9"))
                .thenReturn(Optional.of(event));
        when(syncStateRepository.findByCourseId("c1")).thenReturn(Optional.of(
                CourseKnowledgeSyncState.builder().courseId("c1").kbVersion(0L).build()));
        when(skillamaAiClient.getKnowledgeBaseSyncStatus("job-9"))
                .thenReturn(Map.of("status", "COMPLETE", "statistics", Map.of("numberOfNewDocumentsIndexed", 1)));

        service.getSyncStatus("c1", "job-9");

        assertEquals(CourseKnowledgeSyncEvent.Status.COMPLETE, event.getStatus());
        verify(syncStateRepository).save(any(CourseKnowledgeSyncState.class));
    }

    @Test
    void syncStatusBumpsKbVersionOnCompleteOnly() {
        CourseKnowledgeSyncState state = CourseKnowledgeSyncState.builder().courseId("c1").kbVersion(3L).build();
        when(syncStateRepository.findByCourseId("c1")).thenReturn(Optional.of(state));
        when(syncEventRepository.findByCourseIdAndIngestionJobId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(skillamaAiClient.getKnowledgeBaseSyncStatus("job-10"))
                .thenReturn(Map.of("status", "COMPLETE", "statistics", Map.of()));

        service.getSyncStatus("c1", "job-10");

        assertEquals(4L, state.getKbVersion());
    }
}
