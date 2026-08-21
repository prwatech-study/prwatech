package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Admin view of one enrollment request (user + course names joined in). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollmentRequestDTO {
    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private String courseId;
    private String courseName;
    private String note;
    private String status;
    private String decisionReason;
    private String decidedBy;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
}
