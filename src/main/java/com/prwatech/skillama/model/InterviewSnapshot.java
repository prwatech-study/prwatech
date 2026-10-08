package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Silent proctoring still. Never returned by candidate APIs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSnapshot {
    private String id;
    private Integer offsetMinutes;
    private String contentType;
    /** JPEG/PNG bytes as base64. Admin detail exposes a data URL, not this field. */
    private String imageBase64;
    private Instant capturedAt;
}
