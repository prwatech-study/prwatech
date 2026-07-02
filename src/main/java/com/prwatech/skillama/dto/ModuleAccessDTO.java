package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleAccessDTO {
    private String moduleId;
    private String moduleName;
    private Integer moduleIndex;
    private Boolean isAccessible;
    private Boolean isLocked;
    private String lockReason;          // "Login required", "Previous module incomplete", etc.
    private List<LectureAccessDTO> lectures;
    private Boolean quizRequired;
    private Boolean quizPassed;
    private Integer quizBestScore;
    private String quizLockReason;
}

