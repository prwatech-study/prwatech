package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Admin view of a single learner/guest AI chat exchange. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminChatInteractionDTO {
    private String interactionId;
    private String userId;
    private String userName;
    private String userEmail;
    private Boolean isGuest;
    private String sessionId;
    private String courseId;
    private String courseName;
    private String question;
    private String answer;
    /** True when a spoken answer exists — never the raw (unauthenticated, permanent) ai-tutor URL. */
    private Boolean hasAudio;
    private String lectureContext;
    private String questionType;
    private LocalDateTime timestamp;
}
