package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponseDTO {
    private String userId;
    private Integer assignedCourses;
    private List<EnrollmentDTO> enrollments;
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrollmentDTO {
        private String courseId;
        private String courseName; // Added for better response
        private java.time.LocalDateTime enrolledAt;
    }
}

