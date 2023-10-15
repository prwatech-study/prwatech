package com.prwatech.user.template;

import com.prwatech.user.model.UserFcmToken;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class UserFcmTokenTemplate {

    private final MongoTemplate mongoTemplate;


    public UserFcmToken getByUserId(ObjectId userId){
        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId));
        return mongoTemplate.findOne(query, UserFcmToken.class);
    }
}
