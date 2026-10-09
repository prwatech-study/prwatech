package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Parsed exam blueprint extracted from an official guidelines page. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificationExamMeta {

    private Integer durationMinutes;
    private Integer questionCountMin;
    private Integer questionCountMax;
    @Builder.Default
    private List<String> domains = new ArrayList<>();
    private String formatNotes;
    private LocalDateTime fetchedAt;
}
