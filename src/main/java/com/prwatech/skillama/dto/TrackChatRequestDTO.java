package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackChatRequestDTO {
    private String question;              // Text question or audio transcript
    private String questionType;           // "text" or "audio"
    private String answer;                // AI response text
    private String answerAudioUrl;        // AI response audio URL
    private String lectureContext;        // Current lecture
    private String courseId;
    private LocalDateTime timestamp;
    /** Milliseconds from question send until AI reply was received. */
    private Long responseTimeMs;
    /** Seconds the learner spent speaking (voice questions). */
    private Integer userSpeakDurationSeconds;
    /** Seconds of AI answer audio (when available). */
    private Integer answerAudioDurationSeconds;
}

