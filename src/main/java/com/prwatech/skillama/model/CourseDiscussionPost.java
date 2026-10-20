package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Lightweight peer/cohort discussion post for a course. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "course_discussion_posts")
public class CourseDiscussionPost {
    @Id
    private String id;

    @Indexed
    private String courseId;

    @Indexed
    private String authorUserId;

    private String authorName;
    private String body;

    @Builder.Default
    private Boolean active = Boolean.TRUE;

    private LocalDateTime createdAt;
}
