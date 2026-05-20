package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "reviews")
public class Review {
    @Id
    private String id;
    private String userId;
    private String courseId;
    private String courseName;
    private int rating;
    private String comment;
    private String review;
    private String profession;
    private ReviewScope scope;
    private String userName;
    private String userEmail;
    private ReviewStatus status = ReviewStatus.OPEN;
    private String teamReply;
    private LocalDateTime repliedAt;
    private String repliedBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum ReviewScope {
        COURSE, OVERALL
    }

    public enum ReviewStatus {
        OPEN, REPLIED, CLOSED
    }
}
