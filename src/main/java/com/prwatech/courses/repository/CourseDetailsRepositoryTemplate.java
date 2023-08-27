package com.prwatech.courses.repository;

import com.prwatech.courses.model.CourseDetails;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

  public Page<CourseDetails> getAllMostPopularCourses(Integer pageNumber, Integer pageSize) {
    Pageable pageable = PageRequest.of(pageNumber, pageSize);
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Types").in("Classroom"));
    Long count = mongoTemplate.count(query, CourseDetails.class);
    query.with(pageable);
    List<CourseDetails> courseDetailsList = mongoTemplate.find(query, CourseDetails.class);
    return new PageImpl<>(courseDetailsList, pageable, count);
  }

  public Page<CourseDetails> getAllSelfPlacedCourses(Integer pageNumber, Integer pageSize) {
    Pageable pageable = PageRequest.of(pageNumber, pageSize);
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Types").in("Online"));
    Long count = mongoTemplate.count(query, CourseDetails.class);
    query.with(pageable);
    List<CourseDetails> courseDetailsList = mongoTemplate.find(query, CourseDetails.class);
    return new PageImpl<>(courseDetailsList, pageable, count);
  }

  public Page<CourseDetails> getAllFreeCourses(Integer pageNumber, Integer pageSize) {
    Pageable pageable = PageRequest.of(pageNumber, pageSize);
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Types").in("Webinar"));
    Long count = mongoTemplate.count(query, CourseDetails.class);
    query.with(pageable);
    List<CourseDetails> courseDetailsList = mongoTemplate.find(query, CourseDetails.class);
    return new PageImpl<>(courseDetailsList, pageable, count);
  }

  public List<CourseDetails> searchByName(String name){
    Criteria nameCriteria = Criteria.where("Course_Title").regex(name, "i");
    Query query= new Query(nameCriteria);
   return mongoTemplate.find(query, CourseDetails.class);
  }
}
