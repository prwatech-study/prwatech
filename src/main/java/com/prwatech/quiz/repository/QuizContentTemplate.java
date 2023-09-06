package com.prwatech.quiz.repository;

import com.prwatech.quiz.model.QuizContent;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class QuizContentTemplate {

    private final MongoTemplate mongoTemplate;

    public List<QuizContent> findByQuizId(ObjectId id){
        Query query = new Query();
        query.addCriteria(Criteria.where("quiz_id").is(id)
                .andOperator(Criteria.where("is_deleted").is(Boolean.FALSE)));
        return mongoTemplate.find(query, QuizContent.class);
    }
}
