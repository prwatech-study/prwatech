package com.prwatech.courses.repository;

import com.prwatech.courses.dto.CourseDetailsProjection;
import com.prwatech.courses.dto.CourseTypeProjection;
import com.prwatech.courses.model.CourseDetails;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class CourseDetailsRepositoryTemplate {

  private final MongoTemplate mongoTemplate ;

  //Webinar - Online
  //Online - Self Paced
  //Classroom - Class Room
  public List<CourseDetails> getMostPopularCourse(String platform) {
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Types").in("Webinar"));
    if("IOS".equalsIgnoreCase(platform)) {
      query.addCriteria(Criteria.where("Product_Id_Available_Webinar").is(true));
    }
    query.limit(10);
    return mongoTemplate.find(query, CourseDetails.class);
  };

  public List<CourseDetails> getSelfPlacedCourses(String platform) {
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Types").in("Online"));
    if("IOS".equalsIgnoreCase(platform)) {
      query.addCriteria(Criteria.where("Product_Id_Available_Online").is(true));
    }
    query.limit(10);
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
    query.addCriteria(Criteria.where("Course_Types").in("Webinar"));
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

  public List<CourseDetailsProjection> searchByName(String name){

    AggregationOperation match = Aggregation.match(Criteria.where("Course_Title").regex(name, "i"));
    AggregationOperation project = Aggregation.project("id", "Course_Title");
    Aggregation aggregation = Aggregation.newAggregation(match, project);

    AggregationResults<CourseDetailsProjection> results = mongoTemplate.aggregate(aggregation, "CourseDetails", CourseDetailsProjection.class);
    return results.getMappedResults();
  }

  public List<CourseTypeProjection> getCourseTypeByCourseId(List<String> courseIds){
    AggregationOperation match = Aggregation.match(Criteria.where("id").in(courseIds));
    AggregationOperation project = Aggregation.project("Course_Type");
    Aggregation aggregation = Aggregation.newAggregation(match, project);

    AggregationResults<CourseTypeProjection> results = mongoTemplate.aggregate(aggregation,"CourseDetails", CourseTypeProjection.class);
    return results.getMappedResults();
  }

  public Page<CourseDetails> getFreeCourseByBit(Integer pageNumber, Integer pageSize){
    Pageable pageable = PageRequest.of(pageNumber, pageSize);
    Query query = new Query();
    query.addCriteria(Criteria.where("isFree").in(Boolean.TRUE));
    Long count = mongoTemplate.count(query, CourseDetails.class);
    query.with(pageable);
    List<CourseDetails> courseDetailsList = mongoTemplate.find(query, CourseDetails.class);
    return new PageImpl<>(courseDetailsList, pageable, count);
  }

  public Page<CourseDetails> getAllCourses(Integer pageNumber, Integer pageSize, String courseCategoryId, String platform) {
    Pageable pageable = PageRequest.of(pageNumber, pageSize);
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_CategoryId").is(new ObjectId(courseCategoryId)).and("Course_Status").is(0));
    if("IOS".equalsIgnoreCase(platform)) {
      query.addCriteria(Criteria.where("Product_Id_Available").is(true));
    }
    Long count = mongoTemplate.count(query, CourseDetails.class);
    query.with(pageable);
    List<CourseDetails> courseDetailsList = mongoTemplate.find(query, CourseDetails.class);
    return new PageImpl<>(courseDetailsList, pageable, count);
  }
}
