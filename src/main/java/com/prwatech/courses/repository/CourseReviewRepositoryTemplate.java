package com.prwatech.courses.repository;

import com.mongodb.client.AggregateIterable;
import com.prwatech.courses.dto.CourseDetailsProjection;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.model.CourseReview;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class CourseReviewRepositoryTemplate {

  private MongoTemplate mongoTemplate;

  public List<CourseReview> getCourseReviewByCourseId(ObjectId courseId) {

    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Id").is(courseId));
    query.fields().include("Review_Number").include("Review_Message");
    return mongoTemplate.find(query, CourseReview.class);
  }

  public CourseReview getCourseReviewByCourseIdAndUserId(ObjectId userId, ObjectId courseId){
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Id")
            .is(courseId).andOperator(Criteria.where("Reviewer_Id").is(userId)));
    return mongoTemplate.findOne(query, CourseReview.class);
  }
}
