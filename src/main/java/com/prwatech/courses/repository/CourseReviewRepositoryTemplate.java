package com.prwatech.courses.repository;

import com.prwatech.courses.model.CourseReview;
import java.util.List;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
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

    return mongoTemplate.find(query, CourseReview.class);
  }

  public CourseReview getCourseReviewByCourseIdAndUserId(ObjectId userId, ObjectId courseId){
    Query query = new Query();
    query.addCriteria(Criteria.where("Course_Id")
            .is(courseId).andOperator(Criteria.where("Reviewer_Id").is(userId)));
    return mongoTemplate.findOne(query, CourseReview.class);
  }
}
