package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class TextToAudioRequestDTO {
    private String text;
    /** Optional: {@code en} | {@code hi} — selects Polly voice (Kajal when hi). */
    private String preferredLanguage;
}
