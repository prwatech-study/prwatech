package com.prwatech.finance.repository.template;

import com.prwatech.finance.model.UserOrder;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@AllArgsConstructor
public class UserOrderTemplate {

    private final MongoTemplate mongoTemplate;

    public UserOrder getByUserIdAndCourseId(ObjectId userId, ObjectId courseId){
        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId))
                .addCriteria(Criteria.where("course_id").is(courseId));

        return mongoTemplate.findOne(query, UserOrder.class);
    }

    public UserOrder getByOrderId(String orderId, ObjectId userId){
        Query query = new Query();
        query.addCriteria(Criteria.where("order_id").is(orderId))
                .addCriteria(Criteria.where("user_id").is(userId));
        return mongoTemplate.findOne(query, UserOrder.class);
    }

    public List<UserOrder> getByUserIdAndCourseIds(ObjectId userId, List<ObjectId> courseIds){
        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId))
                .addCriteria(Criteria.where("course_id").in(courseIds));

        return mongoTemplate.find(query, UserOrder.class);
    }

    public List<UserOrder> getAllEnrolledCourses(ObjectId userId){
        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId))
                .addCriteria(Criteria.where("isCompleted").is(Boolean.TRUE));

        return mongoTemplate.find(query, UserOrder.class);
    }
}
