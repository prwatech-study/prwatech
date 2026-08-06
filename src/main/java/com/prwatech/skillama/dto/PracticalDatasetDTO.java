package com.prwatech.skillama.dto;

import lombok.*;

import java.time.LocalDateTime;

/** Admin-facing view of a practical dataset. Deliberately has no field for the S3 storage key. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticalDatasetDTO {
    private String datasetId;
    private String displayName;
    private long fileSizeBytes;
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;
}
