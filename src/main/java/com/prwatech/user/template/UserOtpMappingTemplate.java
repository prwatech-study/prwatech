package com.prwatech.user.template;

import com.prwatech.user.model.UserOtpMapping;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class UserOtpMappingTemplate {

  MongoTemplate mongoTemplate;

  public Optional<UserOtpMapping> findOtpMappingByUserId(String userId) {
    Query query = new Query().addCriteria(Criteria.where("user_id").is(userId));
    return Optional.ofNullable(mongoTemplate.findOne(query, UserOtpMapping.class));
  }

  public void deleteOtpHistory(String userId, Long phone) {
    Query query =
        new Query().addCriteria(Criteria.where("user_id").is(userId).and("Phone_Number").is(phone));
    mongoTemplate.remove(query, UserOtpMapping.class);
  }
}
