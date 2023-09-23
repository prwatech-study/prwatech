package com.prwatech.quiz.repository;

import com.prwatech.quiz.model.QuizContentAttemptMap;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class QuizAttemptMapTemplate {

    private final MongoTemplate mongoTemplate;


    public QuizContentAttemptMap findByUserIdQuizIdContentId(ObjectId userId, ObjectId contentId){
        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId)
                .andOperator(Criteria.where("quiz_content_id").is(contentId)));
        return mongoTemplate.findOne(query, QuizContentAttemptMap.class);
    }
}
