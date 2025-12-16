package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    private Long totalUsers;
    private Long activeUsers;
    private Long totalCourses;
    private Long activeCourses;
    private Long totalEnrollments;
    private Double averageProgress;
    private Integer recentUsers;
    private Integer recentCourses;
}

