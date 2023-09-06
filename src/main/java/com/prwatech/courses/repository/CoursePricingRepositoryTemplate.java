package com.prwatech.courses.repository;

import com.prwatech.courses.model.Pricing;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class CoursePricingRepositoryTemplate {

  private MongoTemplate mongoTemplate;

  public Optional<Pricing> getPricingOfCourseByCourseId(ObjectId Course_Id, String Course_Type) {

    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Id").is(Course_Id).and("Course_Type").is(Course_Type));
    return Optional.ofNullable(mongoTemplate.findOne(query, Pricing.class));
  }
}
