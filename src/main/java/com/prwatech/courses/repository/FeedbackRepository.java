package com.prwatech.courses.repository;

import com.prwatech.courses.model.Feedback;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends MongoRepository<Feedback, String> {
    List<Feedback> findByCourseId(String courseId);
    List<Feedback> findByUserId(String userId);
}
