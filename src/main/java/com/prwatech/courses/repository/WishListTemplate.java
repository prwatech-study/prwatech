package com.prwatech.courses.repository;

import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.model.WishList;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@AllArgsConstructor
@Component
public class WishListTemplate {


    private final MongoTemplate mongoTemplate;

    public List<WishList> getWishListByUserId(ObjectId userId){

        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId));
        return mongoTemplate.find(query, WishList.class);
    }

    public Optional<WishList> getByUserIdAndCourseId(ObjectId userId, ObjectId courseId){
        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId)
                        .and("course_id").is(courseId));

        return Optional.ofNullable(mongoTemplate.findOne(query, WishList.class));
    }
}
