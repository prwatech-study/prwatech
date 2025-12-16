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
@Document(collection = "user_lecture_progress")
public class UserLectureProgress {
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    @Indexed
    private String courseId;
    
    @Indexed
    private String lectureId; // This is the submodule label/id
    
    private String moduleName;
    private String lectureName;
    private Boolean completed;
    private LocalDateTime completedAt;
    private Integer timeSpent; // in seconds
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

