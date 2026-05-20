package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_course_enrollments")
public class UserCourseEnrollment {
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    @Indexed
    private String courseId;
    
    private EnrollmentType enrollmentType; // ASSIGNED, PURCHASED
    private LocalDateTime enrolledAt;
    private EnrollmentStatus status; // ACTIVE, INACTIVE, COMPLETED
    
    public enum EnrollmentType {
        ASSIGNED, PURCHASED, DEFAULT_FREEMIUM
    }
    
    public enum EnrollmentStatus {
        ACTIVE, INACTIVE, COMPLETED
    }
}

