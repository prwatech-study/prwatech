package com.prwatech.courses.repository;

import com.prwatech.courses.model.CourseTrack;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class CourseTrackTemplate {

    private final MongoTemplate mongoTemplate;

    public CourseTrack getByCourseIdAndUserId(ObjectId userId, ObjectId courseId){

        Query query = new Query();
        query.addCriteria(Criteria.where("user_id")
                .is(userId).andOperator(Criteria.where("course_id").is(courseId)));

        return mongoTemplate.findOne(query, CourseTrack.class);
    }


    public List<CourseTrack> getCompletedCourseByUserId(ObjectId userId){
        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId).andOperator(Criteria.where("is_all_completed").is(Boolean.TRUE))
        );

        return mongoTemplate.find(query, CourseTrack.class);
    };

}
