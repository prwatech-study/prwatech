package com.prwatech.courses.repository;

import com.prwatech.courses.model.CourseReview;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@EnableMongoRepositories
@Repository
public interface CourseReviewRepository extends MongoRepository<CourseReview, String> {
}
