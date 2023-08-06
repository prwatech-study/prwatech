package com.prwatech.quiz.repository;

import com.prwatech.quiz.model.QuizUserMapping;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class QuizUserTemplate {

   private final MongoTemplate mongoTemplate;

   public QuizUserMapping getByUserIdAndQuizId(ObjectId userId, ObjectId quizId){
       Query query = new Query();
       query.addCriteria(Criteria.where("user_id").is(userId).andOperator(
               Criteria.where("quiz_id").is(quizId)
       ));

       return mongoTemplate.findOne(query, QuizUserMapping.class);
   }

   public QuizUserMapping getByUserIdAndOrderId(ObjectId userId, String orderId){
       Query query = new Query();
       query.addCriteria(Criteria.where("user_id").is(userId).andOperator(
               Criteria.where("order_id").is(orderId)
       ));

       return mongoTemplate.findOne(query, QuizUserMapping.class);
   }
}
