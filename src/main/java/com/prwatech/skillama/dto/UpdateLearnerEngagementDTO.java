package com.prwatech.skillama.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateLearnerEngagementDTO {
    /** {@code en} or {@code hi} — hi only accepted when platform hindiTutorEnabled. */
    private String preferredLocale;
    private String preferredPace;
    private List<String> facts;
    private List<String> weakTopics;
    private String summary;
    /** When true, records today as a learning activity day (streak). */
    private Boolean recordActivity;
}
