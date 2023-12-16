package com.prwatech.quiz.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.persistence.Id;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "quiz_content_attempt_map")
public class QuizContentAttemptMap {

    @Id
    private String id;

    @Field(name = "quiz_id")
    private ObjectId quizId;

    @Field(name = "user_id")
    private ObjectId userId;

    @Field(name = "quiz_content_id")
    private ObjectId quizContentId;

    @Field(name = "attempt")
    private Integer attempt=0;

    @Field(name = "last_score")
    private Integer lastScore;

    @Field(name = "current_score")
    private Integer currentScore;

    @Field(name = "un_attempted")
    private Integer unAttempted=0;

    @Field(name = "created_at")
    @CreationTimestamp
    private LocalDateTime created_at;

    @Field(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updated_at;
}
