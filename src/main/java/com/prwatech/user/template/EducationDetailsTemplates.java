package com.prwatech.user.template;

import com.prwatech.user.model.UserEducationDetails;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class EducationDetailsTemplates {

  private final MongoTemplate mongoTemplate;

  public UserEducationDetails getByUserId(String userId) {
    Query query = new Query(Criteria.where("User_Id").is(userId));

    return mongoTemplate.findOne(query, UserEducationDetails.class);
  }
}
