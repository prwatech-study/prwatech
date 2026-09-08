package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamCourseProgressDTO {
    private String courseId;
    private String courseName;
    private int enrolledLearners;
    private double averageProgress;
    private int completedLearners;
}
