package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One row of a user's course-share reward history — for the "My Shares" transparency view. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseShareHistoryItemDTO {
    private String courseId;
    private String courseName;
    /** INSTAGRAM or LINKEDIN. */
    private String platform;
    private Integer creditsEarned;
    private LocalDateTime sharedAt;
}
