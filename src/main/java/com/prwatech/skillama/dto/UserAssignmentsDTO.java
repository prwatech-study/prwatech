package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserAssignmentsDTO {
    private String userId;
    private String userName;
    private List<CourseAssignmentDTO> courses;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseAssignmentDTO {
        private String courseId;
        private String courseName;
        private java.time.LocalDateTime enrolledAt;
        private Integer progress;
    }
}

