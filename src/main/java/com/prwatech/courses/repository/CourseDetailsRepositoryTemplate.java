package com.prwatech.courses.repository;

import com.prwatech.courses.model.CourseDetails;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class CourseDetailsRepositoryTemplate {

  private final MongoTemplate mongoTemplate;

  public List<CourseDetails> getMostPopularCourse() {
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Types").in("Classroom")).limit(10);
    return mongoTemplate.find(query, CourseDetails.class);
  };

  public List<CourseDetails> getSelfPlacedCourses() {
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Types").in("Online")).limit(10);
    return mongoTemplate.find(query, CourseDetails.class);
  }

  public List<CourseDetails> getFreeCourses() {
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Types").in("Webinar")).limit(10);
    return mongoTemplate.find(query, CourseDetails.class);
  }
}
