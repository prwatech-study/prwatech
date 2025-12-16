package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseAnalyticsDTO {
    private String courseId;
    private String courseName;
    private Long totalEnrollments;
    private Long activeEnrollments;
    private Long completedEnrollments;
    private Double averageProgress;
    private Double completionRate;
}

