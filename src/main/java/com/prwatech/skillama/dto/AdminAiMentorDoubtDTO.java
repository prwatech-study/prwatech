package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.DoubtStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Admin view of a single AI Mentor doubt (question + latest AI answer). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAiMentorDoubtDTO {
    private String doubtId;
    private String userId;
    private String userName;
    private String userEmail;
    private String courseId;
    private String courseName;
    private String moduleId;
    private String lessonId;
    private DoubtStatus status;
    private String question;
    private String latestAnswer;
    private int messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
