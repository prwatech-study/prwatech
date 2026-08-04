package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A real link into a course module/submodule. Only ever built when a match
 * was actually found against the CURRENT curriculum — never a guess, and
 * never stale — so {@code moduleName}/{@code submoduleLabel} always reflect
 * today's content, even if it was renamed since the exam attempt.
 * The frontend's lecture player resolves a lecture by these display strings,
 * not by id, so both are included alongside the ids.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AreaLinkDTO {
    private String courseId;
    private String curriculumModuleId;
    private String submoduleId;
    private String moduleName;
    private String submoduleLabel;
}
