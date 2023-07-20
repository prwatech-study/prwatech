package com.prwatech.courses.repository;

import com.prwatech.courses.model.CourseCurriculam;
import java.util.List;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class CourseCurriculamTemplate {

  private final MongoTemplate mongoTemplate;

  public CourseCurriculam getAllCurriculamByCourseId(ObjectId courseId) {

    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Id").is(courseId));
    return mongoTemplate.findOne(query, CourseCurriculam.class);
  }
}
