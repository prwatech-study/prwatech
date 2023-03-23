package com.prwatech.user.repository;

import com.prwatech.user.model.Users;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
public class IamMongodbTemplateLayer {

    MongoTemplate mongoTemplate;

    public Optional<Users> findByEmail(String email){
        Query query = new Query().addCriteria((Criteria.where("Email").is(email)));

        return Optional.of(mongoTemplate.findOne(query, Users.class));
    }

    public Optional<Users> findByMobile(Long mobileNumber){
        Query query = new Query().addCriteria(Criteria.where("PhoneNumber").is(mobileNumber));
        return Optional.of(mongoTemplate.findOne(query, Users.class));
    }
}
