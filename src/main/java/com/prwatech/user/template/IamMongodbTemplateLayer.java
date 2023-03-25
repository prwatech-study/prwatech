package com.prwatech.user.template;

import com.prwatech.user.model.User;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class IamMongodbTemplateLayer {

  MongoTemplate mongoTemplate;

  public Optional<User> findByEmail(String email) {
    Query query = new Query().addCriteria((Criteria.where("Email").is(email)));

    return Optional.ofNullable(mongoTemplate.findOne(query, User.class));
  }

  public Optional<User> findByMobile(Long mobileNumber) {
    Query query = new Query().addCriteria(Criteria.where("PhoneNumber").is(mobileNumber));
    return Optional.ofNullable(mongoTemplate.findOne(query, User.class));
  }
}
