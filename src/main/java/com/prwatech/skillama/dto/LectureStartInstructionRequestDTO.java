package com.prwatech.skillama.dto;

import lombok.Data;

import java.util.List;

@Data
public class LectureStartInstructionRequestDTO {
    private List<String> topics;
    /** Optional {@code en} or {@code hi}. */
    private String preferredLanguage;
}
