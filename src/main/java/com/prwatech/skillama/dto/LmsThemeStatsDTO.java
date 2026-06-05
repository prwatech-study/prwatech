package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LmsThemeStatsDTO {
    /** Times learners switched to Classic */
    private long classic;
    /** Times learners switched to Aurora */
    private long aurora;
    private long totalSwitches;
    /** Learners whose saved preference is Classic */
    private long activeClassic;
    /** Learners whose saved preference is Aurora */
    private long activeAurora;
}
