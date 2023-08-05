package com.prwatech.quiz.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.persistence.Id;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "quiz")
public class Quiz {

    @Id
    private String id;

    @Field(name = "quiz_name")
    private String quizName;

    @Field(name = "secondary_title")
    private String secondaryTitle;

    @Field(name = "description")
    private String description;

    @Field(name = "templateUrl")
    private String templateUrl;

    @Field(name = "why_this_quiz")
    private List<String> whyThisQuiz=new ArrayList<>();

    @Field(name = "quiz_content")
    private List<QuizContent> quizContents=new ArrayList<>();

    @Field(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
