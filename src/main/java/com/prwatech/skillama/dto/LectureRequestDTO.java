package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class LectureRequestDTO {
    private String section;
    private String courseId;
    /** Optional {@code en} or {@code hi} — Hindi lecture narration when platform Hindi is on. */
    private String preferredLanguage;
}
