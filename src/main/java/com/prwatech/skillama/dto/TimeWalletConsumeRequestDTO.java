package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class TimeWalletConsumeRequestDTO {
    /** Active seconds the client claims since its last beat (server clamps). */
    private Integer seconds;
    /** Feature surface, e.g. ai_tutor / ai_mentor / ai_exam. */
    private String module;
}
