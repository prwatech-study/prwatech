package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkReconcileProgressResultDTO {
    private boolean dryRun;
    private int enrollmentsProcessed;
    private int uniqueUsers;
    private int uniqueCourses;
    private int totalLecturesSynced;
    private int failures;
    @Builder.Default
    private List<String> failureSamples = new ArrayList<>();
}
