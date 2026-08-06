package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Admin view of a single AI Exam attempt. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminExamAttemptDTO {
    private String attemptId;
    private String userId;
    private String userName;
    private String userEmail;
    private String courseId;
    private String courseName;
    private String moduleId;
    private String topic;
    private ExamDifficulty difficulty;
    private ExamType examType;
    private Integer score;
    private Integer maxScore;
    private Double percentage;
    private Integer timeSpentSeconds;
    private Boolean overTimeLimit;
    private LocalDateTime submittedAt;
    private Boolean passed;
    /** AI-generated at submission — surfaced here so admins can spot-check quality without querying Mongo directly. */
    private String overallFeedback;
    private String recommendationText;
}
