package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberCourseProgressDTO {
    private String userId;
    private String userName;
    private String courseId;
    private String courseName;
    private int progress;
    private Integer completedLectures;
    private Integer totalLectures;
    private java.time.LocalDateTime lastAccessed;
}
