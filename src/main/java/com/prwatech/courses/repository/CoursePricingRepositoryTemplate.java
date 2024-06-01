package com.prwatech.courses.repository;

import com.prwatech.courses.enums.CourseLevelCategory;
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

  public Optional<Pricing> getPricingOfCourseByCourseId(ObjectId Course_Id, CourseLevelCategory Course_Type, String platform) {

    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Id").is(Course_Id).andOperator(Criteria.where("Course_Type").is(Course_Type.getCourseType())));
    if ("IOS".equalsIgnoreCase(platform)) {
      query.addCriteria(Criteria.where("Product_Id").exists(true));
    }
    return Optional.ofNullable(mongoTemplate.findOne(query, Pricing.class));
  }
}
