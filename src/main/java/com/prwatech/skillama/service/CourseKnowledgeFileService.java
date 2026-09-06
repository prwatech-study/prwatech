package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.KnowledgeBaseFileDTO;
import com.prwatech.skillama.dto.KnowledgeBaseSyncEventDTO;
import com.prwatech.skillama.dto.KnowledgeBaseSyncResponseDTO;
import com.prwatech.skillama.dto.KnowledgeBaseSyncStatusDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CourseKnowledgeFile;
import com.prwatech.skillama.model.CourseKnowledgeSyncEvent;
import com.prwatech.skillama.model.CourseKnowledgeSyncState;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseKnowledgeFileRepository;
import com.prwatech.skillama.repository.CourseKnowledgeSyncEventRepository;
import com.prwatech.skillama.repository.CourseKnowledgeSyncStateRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseKnowledgeFileService {

    private final CourseKnowledgeFileRepository fileRepository;
    private final CourseKnowledgeSyncEventRepository syncEventRepository;
    private final CourseKnowledgeSyncStateRepository syncStateRepository;
    private final FileStorageService fileStorageService;
    private final CourseService courseService;
    private final UserService userService;
    private final SkillamaAiClient skillamaAiClient;

    @Value("${file.upload.s3.knowledge-base-prefix:knowledge-base}")
    private String knowledgeBasePrefix;

    @Value("${skillama.kb.embed-rate-per-mtoken:0.02}")
    private double embedRatePerMtoken;

    @Value("${skillama.kb.owner-approval-usd:2}")
    private double ownerApprovalUsd;

    public List<KnowledgeBaseFileDTO> listForCourse(String courseId) {
        ensureCourseExists(courseId);
        return fileRepository.findByCourseIdAndDeletedAtIsNullOrderByUploadedAtDesc(courseId).stream()
                .map(this::toFileDto)
                .collect(Collectors.toList());
    }

    public KnowledgeBaseFileDTO upload(
            String courseId,
            MultipartFile file,
            String replaceFileId,
            String adminUserId) throws IOException {
        ensureCourseExists(courseId);

        String fileName = extractFileName(file.getOriginalFilename());
        String s3Key;
        CourseKnowledgeFile record;

        if (StringUtils.hasText(replaceFileId)) {
            record = fileRepository.findByIdAndCourseIdAndDeletedAtIsNull(replaceFileId, courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Knowledge base file not found"));
            s3Key = record.getS3Key();
            record.setFileName(fileName);
            record.setContentType(file.getContentType());
            record.setSize(file.getSize());
            record.setEstimatedTokens(estimateTokens(file));
            record.setUploadedBy(adminUserId);
            record.setUploadedAt(IndiaTime.now());
        } else {
            s3Key = knowledgeBasePrefix + "/" + courseId + "/" + sanitizeForKey(fileName);
            record = CourseKnowledgeFile.builder()
                    .courseId(courseId)
                    .fileName(fileName)
                    .s3Key(s3Key)
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .estimatedTokens(estimateTokens(file))
                    .uploadedBy(adminUserId)
                    .uploadedAt(IndiaTime.now())
                    .build();
        }

        fileStorageService.uploadKnowledgeBaseDocument(file, courseId, s3Key);
        return toFileDto(fileRepository.save(record));
    }

    public void delete(String courseId, String fileId) throws IOException {
        CourseKnowledgeFile file = fileRepository.findByIdAndCourseIdAndDeletedAtIsNull(fileId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge base file not found"));

        if (StringUtils.hasText(file.getS3Key())) {
            fileStorageService.deleteKnowledgeBaseDocument(file.getS3Key());
        }
        file.setDeletedAt(IndiaTime.now());
        fileRepository.save(file);
    }

    public KnowledgeBaseSyncResponseDTO sync(String courseId, String adminUserId) {
        ensureCourseExists(courseId);
        User user = userService.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        long pendingTokens = pendingTokensSinceLastSync(courseId);
        double estCost = estimateCostUsd(pendingTokens);
        requireOwnerIfOverThreshold(user, estCost);

        int fileCount = fileRepository.findByCourseIdAndDeletedAtIsNullOrderByUploadedAtDesc(courseId).size();
        Map<String, Object> aiResponse = skillamaAiClient.startKnowledgeBaseSync(courseId);
        String jobId = String.valueOf(aiResponse.get("ingestionJobId"));
        String status = String.valueOf(aiResponse.getOrDefault("status", "STARTED"));

        syncEventRepository.save(CourseKnowledgeSyncEvent.builder()
                .courseId(courseId)
                .ingestionJobId(jobId)
                .triggeredBy(adminUserId)
                .triggeredAt(IndiaTime.now())
                .status(CourseKnowledgeSyncEvent.Status.STARTED)
                .fileCountAtSync(fileCount)
                .estCostUsd(estCost)
                .build());

        CourseKnowledgeSyncState state = syncStateRepository.findByCourseId(courseId)
                .orElse(CourseKnowledgeSyncState.builder().courseId(courseId).kbVersion(0L).build());
        state.setLastIngestionJobId(jobId);
        state.setLastSyncStatus(status);
        syncStateRepository.save(state);

        return KnowledgeBaseSyncResponseDTO.builder()
                .ingestionJobId(jobId)
                .status(status)
                .estimatedCostUsd(estCost)
                .pendingTokens(pendingTokens)
                .build();
    }

    public KnowledgeBaseSyncStatusDTO getSyncStatus(String courseId, String jobId) {
        ensureCourseExists(courseId);
        Map<String, Object> aiStatus = skillamaAiClient.getKnowledgeBaseSyncStatus(jobId);
        String status = String.valueOf(aiStatus.getOrDefault("status", "UNKNOWN"));
        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) aiStatus.getOrDefault("statistics", Map.of());

        updateSyncEventOnTerminal(courseId, jobId, status, statistics);

        return KnowledgeBaseSyncStatusDTO.builder()
                .status(status)
                .statistics(statistics)
                .build();
    }

    public Page<KnowledgeBaseSyncEventDTO> getSyncHistory(String courseId, int page, int size) {
        ensureCourseExists(courseId);
        Pageable pageable = PageRequest.of(page, size);
        return syncEventRepository.findByCourseIdOrderByTriggeredAtDesc(courseId, pageable)
                .map(this::toEventDto);
    }

    private void updateSyncEventOnTerminal(
            String courseId, String jobId, String status, Map<String, Object> statistics) {
        if (!isTerminalStatus(status)) {
            return;
        }
        syncEventRepository.findByCourseIdAndIngestionJobId(courseId, jobId)
                .ifPresent(event -> {
                    event.setStatus("COMPLETE".equalsIgnoreCase(status)
                            ? CourseKnowledgeSyncEvent.Status.COMPLETE
                            : CourseKnowledgeSyncEvent.Status.FAILED);
                    event.setStatistics(statistics);
                    syncEventRepository.save(event);
                });

        CourseKnowledgeSyncState state = syncStateRepository.findByCourseId(courseId)
                .orElse(CourseKnowledgeSyncState.builder().courseId(courseId).kbVersion(0L).build());
        state.setLastSyncStatus(status);
        if ("COMPLETE".equalsIgnoreCase(status)) {
            state.setLastSyncedAt(IndiaTime.now());
            long version = state.getKbVersion() != null ? state.getKbVersion() : 0L;
            state.setKbVersion(version + 1);
        }
        syncStateRepository.save(state);
    }

    private long pendingTokensSinceLastSync(String courseId) {
        LocalDateTime lastSyncedAt = syncStateRepository.findByCourseId(courseId)
                .map(CourseKnowledgeSyncState::getLastSyncedAt)
                .orElse(null);

        return fileRepository.findByCourseIdAndDeletedAtIsNullOrderByUploadedAtDesc(courseId).stream()
                .filter(f -> lastSyncedAt == null || f.getUploadedAt() == null || f.getUploadedAt().isAfter(lastSyncedAt))
                .mapToLong(f -> f.getEstimatedTokens() != null ? f.getEstimatedTokens() : 0L)
                .sum();
    }

    private double estimateCostUsd(long pendingTokens) {
        return pendingTokens / 1_000_000.0 * embedRatePerMtoken;
    }

    private void requireOwnerIfOverThreshold(User user, double estCost) {
        if (estCost <= ownerApprovalUsd) {
            return;
        }
        if (user.getRole() != User.UserRole.OWNER) {
            throw new RuntimeException("Owner approval required");
        }
    }

    private boolean isTerminalStatus(String status) {
        return "COMPLETE".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status);
    }

    private void ensureCourseExists(String courseId) {
        courseService.findActiveById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    private long estimateTokens(MultipartFile file) {
        return Math.max(1L, file.getSize() / 4);
    }

    private String extractFileName(String name) {
        if (!StringUtils.hasText(name)) {
            return "document";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String sanitizeForKey(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private KnowledgeBaseFileDTO toFileDto(CourseKnowledgeFile file) {
        return KnowledgeBaseFileDTO.builder()
                .id(file.getId())
                .courseId(file.getCourseId())
                .fileName(file.getFileName())
                .s3Key(file.getS3Key())
                .contentType(file.getContentType())
                .size(file.getSize())
                .estimatedTokens(file.getEstimatedTokens())
                .uploadedBy(file.getUploadedBy())
                .uploadedAt(file.getUploadedAt())
                .build();
    }

    private KnowledgeBaseSyncEventDTO toEventDto(CourseKnowledgeSyncEvent event) {
        return KnowledgeBaseSyncEventDTO.builder()
                .id(event.getId())
                .courseId(event.getCourseId())
                .ingestionJobId(event.getIngestionJobId())
                .triggeredBy(event.getTriggeredBy())
                .triggeredAt(event.getTriggeredAt())
                .status(event.getStatus() != null ? event.getStatus().name() : null)
                .fileCountAtSync(event.getFileCountAtSync())
                .estCostUsd(event.getEstCostUsd())
                .statistics(event.getStatistics())
                .build();
    }
}
