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
@Document(collection = "user_course_progress")
public class UserCourseProgress {
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    @Indexed
    private String courseId;
    
    private Integer progress; // 0-100
    private Integer totalLectures;
    private Integer completedLectures;
    private LocalDateTime enrolledAt;
    private LocalDateTime lastAccessed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

