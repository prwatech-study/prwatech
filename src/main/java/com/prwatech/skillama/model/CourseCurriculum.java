package com.prwatech.skillama.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "course_curricula")
public class CourseCurriculum {
    @Id
    private String id;
    private String courseId;
    private String moduleName;
    private String moduleAssetPath;
    private List<Submodule> submodules;
    private String title;
    private String content;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer order; // Order of the module in the curriculum

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Submodule {
        /** Stable identifier, immutable once set — labels/order can change, this can't. Backfilled lazily for pre-existing submodules. */
        private String id;
        private String label;
        private String imagePath;
        @JsonProperty("isPracticalRequired")
        private boolean isPracticalRequired;
        private String scriptText;
        private Integer order; // Order of the submodule within the module
        private Boolean enabled;

        /** Links this submodule to a {@code practical_datasets} row — one CSV per exercise. */
        private String datasetId;

        // AI image generation daily-cap tracking (server-side, exploit-proof).
        // Counter resets when the stored date no longer matches "today" (India time).
        private Integer imageGenCountToday;
        private String imageGenCountDate; // ISO yyyy-MM-dd, India time

        /** API-only: set when practical topic is missing scriptText (not stored in Mongo). */
        @Transient
        private String contentIntegrityIssueCode;
        /** API-only: learner-facing explanation (not stored in Mongo). */
        @Transient
        private String contentIntegrityIssueMessage;
    }
}
