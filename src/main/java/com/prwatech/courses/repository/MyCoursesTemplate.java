package com.prwatech.courses.repository;

import com.prwatech.courses.model.MyCourses;
import java.util.List;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class MyCoursesTemplate {

  private final MongoTemplate mongoTemplate;

  public List<MyCourses> findCourseByUserId(ObjectId userId) {
    Query query = new Query();
    query.addCriteria(Criteria.where("User_Id").is(userId));
    return mongoTemplate.find(query, MyCourses.class);
  }
}
