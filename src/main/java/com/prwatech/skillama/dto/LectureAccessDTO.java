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
public class LectureAccessDTO {
    private String lectureLabel;
    private String lectureId;
    private Boolean isAccessible;
    private Boolean isLocked;
    private Boolean isCompleted;
    private Boolean isInProgress;
    private String lockReason;
    private Integer completionPercentage;
    private LocalDateTime unlockedAt;
    private LocalDateTime completedAt;
}

