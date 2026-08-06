package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One point on the score-over-time trend line, oldest first. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreTrendPointDTO {
    private String attemptId;
    private LocalDateTime submittedAt;
    private Double percentage;
    private ExamType examType;
}
