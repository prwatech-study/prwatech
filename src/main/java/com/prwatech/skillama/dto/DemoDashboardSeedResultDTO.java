package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoDashboardSeedResultDTO {
    private String userId;
    private String email;
    private String userName;
    private int coursesSeeded;
    private int averageProgressPercent;
    private List<CourseProgressSummary> courses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseProgressSummary {
        private String courseId;
        private String courseName;
        private int progressPercent;
        private int completedLectures;
        private int totalLectures;
    }
}
