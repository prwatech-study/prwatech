package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteLectureRequestDTO {
    private String lectureLabel;
    private String courseId;
    private String moduleName;
    private Integer timeSpent;              // seconds
    private Integer completionPercentage;    // 0-100
    private LocalDateTime completedAt;
}

