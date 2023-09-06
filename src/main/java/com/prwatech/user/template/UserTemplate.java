package com.prwatech.user.template;

import com.prwatech.user.model.User;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class UserTemplate {

    private final MongoTemplate mongoTemplate;

    public User getByReferalCode(String referalCode){
        Query query = new Query();
        query.addCriteria(Criteria.where("Referal_Code").is(referalCode));
         return mongoTemplate.findOne(query, User.class);
    }
}
