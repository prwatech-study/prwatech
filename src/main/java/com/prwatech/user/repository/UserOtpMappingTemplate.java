package com.prwatech.user.repository;

import com.prwatech.user.model.UserOtpMapping;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
public class UserOtpMappingTemplate {

    MongoTemplate mongoTemplate;

    public Optional<UserOtpMapping> findOtpMappingByUserIdAndPhone(Long userId, Long phone) {
        Query query = new Query().addCriteria(Criteria.where("user_id").is(userId).and("phone_number").is(phone));
        return Optional.of(mongoTemplate.findOne(query, UserOtpMapping.class));
    }

    public void deleteOtpHistory(Long userId, Long phone){
        Query query = new Query().addCriteria(Criteria.where("user_id").is(userId).and("phone_number").is(phone));
        mongoTemplate.remove(query, UserOtpMapping.class);

    }
}
