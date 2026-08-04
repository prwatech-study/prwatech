package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Enough of the original attempt's config for the frontend to offer a one-click retake. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetakeOptionsDTO {
    private ExamType sameExamType;
    private ExamDifficulty sameDifficulty;
    private String sameModuleId;
    private String sameTopic;
    /** True when the attempt passed and difficulty isn't already EXPERT. */
    private Boolean canGoHarder;
    private ExamDifficulty harderDifficulty;
}
