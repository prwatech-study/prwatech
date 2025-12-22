package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleAccessDTO {
    private String moduleId;
    private String moduleName;
    private Integer moduleIndex;
    private Boolean isAccessible;
    private Boolean isLocked;
    private String lockReason;          // "Login required", "Previous module incomplete", etc.
    private List<LectureAccessDTO> lectures;
}

