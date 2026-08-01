package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.ExamAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamAttemptRepository extends MongoRepository<ExamAttempt, String> {
    List<ExamAttempt> findByUserIdOrderBySubmittedAtDesc(String userId);

    List<ExamAttempt> findByUserIdAndCourseIdOrderBySubmittedAtDesc(String userId, String courseId);
}
