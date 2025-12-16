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
public class CourseAssignmentsDTO {
    private String courseId;
    private String courseName;
    private List<UserAssignmentDTO> users;
    private Integer totalEnrollments;
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAssignmentDTO {
        private String userId;
        private String userName;
        private String userEmail;
        private java.time.LocalDateTime enrolledAt;
        private Integer progress;
    }
}

