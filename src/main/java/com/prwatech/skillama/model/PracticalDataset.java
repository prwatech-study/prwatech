package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * The CSV dataset attached to a practical exercise. {@code storageKey} is the real S3 object
 * key and must never be serialized to any DTO — {@code datasetId} is the only identifier ever
 * shown to the AI model or the learner.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "practical_datasets")
public class PracticalDataset {
    @Id
    private String id;

    @Indexed(unique = true)
    private String datasetId; // "ds_xxxxxxxxxxxx"

    @Indexed
    private String courseId;
    private String moduleId;
    private int submoduleIdx;

    private String displayName;  // e.g. "sales.csv" — shown in the learner UI
    private String storageKey;   // S3 key — never serialized outside PracticalDatasetService
    private String contentHash;  // sha256, for duplicate detection within a course
    private long fileSizeBytes;

    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;  // uploadedAt + 90 days; mirrored by an S3 lifecycle rule
    private LocalDateTime deletedAt;  // soft delete — an exercise may still reference this id
}
