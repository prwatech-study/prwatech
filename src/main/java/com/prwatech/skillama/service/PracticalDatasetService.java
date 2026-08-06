package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.PracticalDatasetDTO;
import com.prwatech.skillama.dto.PracticalDatasetSummaryDTO;
import com.prwatech.skillama.exception.DuplicateDatasetException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.PracticalDataset;
import com.prwatech.skillama.repository.PracticalDatasetRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Owns the practical-exercise dataset lifecycle: upload/validate/store, link to a curriculum
 * submodule, and proxy downloads — the S3 storage key never leaves this service. See the
 * "Secure CSV Execution Environment" design proposal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PracticalDatasetService {

    private static final int RETENTION_DAYS = 90;

    private final PracticalDatasetRepository repository;
    private final FileStorageService fileStorageService;
    private final CourseCurriculumService curriculumService;
    private final CourseService courseService;
    private final UserCourseAccessService userCourseAccessService;
    private final MalwareScanService malwareScanService;

    public PracticalDatasetDTO upload(String moduleId, int submoduleIdx, MultipartFile file, String adminUserId) throws IOException {
        CourseCurriculum module = curriculumService.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));
        CourseCurriculum.Submodule submodule = courseService.findSubmodule(moduleId, submoduleIdx)
                .orElseThrow(() -> new ResourceNotFoundException("Submodule not found"));

        fileStorageService.validateCsvDatasetFile(file);

        String courseId = module.getCourseId();
        byte[] bytes = file.getBytes();

        // Scanned before anything else touches this content — a rejection here never reaches
        // the duplicate-hash check, S3, or Mongo.
        malwareScanService.scan(bytes);

        String contentHash = sha256Hex(bytes);
        String previousDatasetId = submodule.getDatasetId();

        repository.findByCourseIdAndContentHashAndDeletedAtIsNull(courseId, contentHash)
                .filter(existing -> !existing.getDatasetId().equals(previousDatasetId))
                .ifPresent(existing -> {
                    throw new DuplicateDatasetException(existing.getDatasetId());
                });

        String datasetId = generateDatasetId();
        String storageKey = fileStorageService.uploadCsvDataset(file, courseId, moduleId, submoduleIdx, datasetId);

        LocalDateTime now = IndiaTime.now();
        PracticalDataset dataset = PracticalDataset.builder()
                .datasetId(datasetId)
                .courseId(courseId)
                .moduleId(moduleId)
                .submoduleIdx(submoduleIdx)
                .displayName(file.getOriginalFilename())
                .storageKey(storageKey)
                .contentHash(contentHash)
                .fileSizeBytes(file.getSize())
                .uploadedBy(adminUserId)
                .uploadedAt(now)
                .expiresAt(now.plusDays(RETENTION_DAYS))
                .build();
        dataset = repository.save(dataset);

        courseService.updateSubmoduleDatasetId(moduleId, submoduleIdx, datasetId);

        // Replacing an existing dataset on this submodule — retire the old row. The S3 object
        // itself is left for the lifecycle rule to expire, same as the DELETE endpoint below.
        if (StringUtils.hasText(previousDatasetId) && !previousDatasetId.equals(datasetId)) {
            softDeleteByDatasetId(previousDatasetId);
        }

        return toDto(dataset);
    }

    public PracticalDatasetDTO getForSubmodule(String moduleId, int submoduleIdx) {
        CourseCurriculum.Submodule submodule = courseService.findSubmodule(moduleId, submoduleIdx)
                .orElseThrow(() -> new ResourceNotFoundException("Submodule not found"));
        if (!StringUtils.hasText(submodule.getDatasetId())) {
            throw new ResourceNotFoundException("No dataset attached to this submodule");
        }
        return toDto(activeByDatasetId(submodule.getDatasetId()));
    }

    public void delete(String moduleId, int submoduleIdx) {
        CourseCurriculum.Submodule submodule = courseService.findSubmodule(moduleId, submoduleIdx)
                .orElseThrow(() -> new ResourceNotFoundException("Submodule not found"));
        String datasetId = submodule.getDatasetId();
        if (!StringUtils.hasText(datasetId)) {
            throw new ResourceNotFoundException("No dataset attached to this submodule");
        }
        softDeleteByDatasetId(datasetId);
        courseService.updateSubmoduleDatasetId(moduleId, submoduleIdx, null);
    }

    /** Learner-facing: {@code {datasetId, displayName}} only, after a course-access check. */
    public PracticalDatasetSummaryDTO getSummary(String userId, String datasetId) {
        PracticalDataset dataset = activeByDatasetId(datasetId);
        userCourseAccessService.assertCanAccessCourse(userId, dataset.getCourseId());
        return PracticalDatasetSummaryDTO.builder()
                .datasetId(dataset.getDatasetId())
                .displayName(dataset.getDisplayName())
                .build();
    }

    /**
     * Proxies the raw CSV bytes after a course-access check — callers never see the S3 storage
     * key. The check lives here rather than only in the controller so any future caller (e.g.
     * the sandboxed-execution endpoint) inherits it automatically.
     */
    public Content download(String userId, String datasetId) throws IOException {
        PracticalDataset dataset = activeByDatasetId(datasetId);
        userCourseAccessService.assertCanAccessCourse(userId, dataset.getCourseId());
        byte[] bytes = fileStorageService.downloadCsvDataset(dataset.getStorageKey());
        return new Content(dataset.getDisplayName(), bytes);
    }

    /** Used by PracticalExecutionService — resolves a dataset for the AI+sandbox flow after the
     * same course-access check every other learner-facing method here applies. Never exposes
     * the storage key past this service boundary; ExecutionContext.storageKey is only read by
     * PracticalSandboxService's invoke payload, never serialized in an HTTP response. */
    public ExecutionContext resolveForExecution(String userId, String datasetId) {
        PracticalDataset dataset = activeByDatasetId(datasetId);
        userCourseAccessService.assertCanAccessCourse(userId, dataset.getCourseId());
        return new ExecutionContext(dataset.getCourseId(), dataset.getStorageKey(), dataset.getDisplayName());
    }

    public record ExecutionContext(String courseId, String storageKey, String displayName) {}

    /**
     * Best-effort column-name hint for the AI — just the header row, never the data itself.
     * Without this, the AI has to guess column names from wording alone (e.g. "revenue" when the
     * real columns are units_sold/unit_price, or "Region" when the real header is lowercase
     * "region"), which produces confidently-wrong code. If this fails for any reason, callers
     * still proceed; the AI just falls back to guessing, same as before this existed.
     */
    public List<String> resolveColumnHint(String storageKey) {
        try {
            byte[] bytes = fileStorageService.downloadCsvDataset(storageKey);
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            int newlineIdx = text.indexOf('\n');
            String headerLine = (newlineIdx >= 0 ? text.substring(0, newlineIdx) : text).strip();
            return java.util.Arrays.stream(headerLine.split(","))
                    .map(String::strip)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            log.warn("Could not read dataset header for column hints; AI will guess column names", e);
            return List.of();
        }
    }

    private PracticalDataset activeByDatasetId(String datasetId) {
        PracticalDataset dataset = repository.findByDatasetId(datasetId)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found"));
        if (dataset.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Dataset is no longer available");
        }
        if (dataset.getExpiresAt() != null && dataset.getExpiresAt().isBefore(IndiaTime.now())) {
            throw new ResourceNotFoundException("Dataset has expired");
        }
        return dataset;
    }

    private void softDeleteByDatasetId(String datasetId) {
        repository.findByDatasetId(datasetId).ifPresent(d -> {
            d.setDeletedAt(IndiaTime.now());
            repository.save(d);
        });
    }

    private PracticalDatasetDTO toDto(PracticalDataset d) {
        return PracticalDatasetDTO.builder()
                .datasetId(d.getDatasetId())
                .displayName(d.getDisplayName())
                .fileSizeBytes(d.getFileSizeBytes())
                .uploadedAt(d.getUploadedAt())
                .expiresAt(d.getExpiresAt())
                .build();
    }

    private static String generateDatasetId() {
        return "ds_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record Content(String fileName, byte[] bytes) {}
}
