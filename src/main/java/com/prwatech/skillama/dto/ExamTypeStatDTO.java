package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** How many attempts of this exam type, and their average score. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamTypeStatDTO {
    private ExamType examType;
    private Integer attemptCount;
    private Double averagePercentage;
}
