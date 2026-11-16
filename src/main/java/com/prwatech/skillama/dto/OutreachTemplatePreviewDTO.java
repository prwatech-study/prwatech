package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OutreachTemplatePreviewDTO {
    private String subjectWave1;
    private String subjectWave2;
    private String subjectWave3;
    private String plainBodySample;
}