package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a backend-initiated Transcribe call (transcript + duration for metering). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscribedAudioDTO {
    private String transcript;
    private double audioSeconds;
}
