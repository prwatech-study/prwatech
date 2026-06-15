package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileProgressResultDTO {
    private String courseId;
    private int syncedLectures;
    private int totalCompletedLectures;
    private int completionPercentage;
}
