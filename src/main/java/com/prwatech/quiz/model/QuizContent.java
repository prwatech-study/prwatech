package com.prwatech.quiz.model;

import com.prwatech.quiz.dto.QuizQuestionDto;
import com.prwatech.quiz.enumuration.QuizCategory;
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
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "quiz_content")
public class QuizContent {

    @Id
    private String id;

    @Field(name = "quiz_category")
    private QuizCategory quizCategory;

    @Field(name = "quiz_id")
    private ObjectId quizId;

    @Field(name = "quiz_question")
    private List<QuizQuestionDto> quizQuestionList;

    @Field(name = "total_mark")
    private Integer totalMark;

    @Field(name = "passing_mark")
    private Integer passingMark;

    @Field(name = "is_deleted")
    private Boolean isDeleted;

    @CreationTimestamp
    @Field(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Field(name = "updated_at")
    private LocalDateTime updatedAt;
}
