package com.prwatech.courses.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "feedbacks")
public class Feedback {
    @Id
    private String id;
    private String courseId;
    private String userId;
    private String userName;
    private String review;
    private Integer rating; // e.g. 1-5 stars
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
