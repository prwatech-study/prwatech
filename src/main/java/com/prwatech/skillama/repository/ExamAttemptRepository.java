package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.ExamAttempt;
import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamAttemptRepository extends MongoRepository<ExamAttempt, String> {
    List<ExamAttempt> findByUserIdOrderBySubmittedAtDesc(String userId);

    List<ExamAttempt> findByUserIdAndCourseIdOrderBySubmittedAtDesc(String userId, String courseId);

    /** Rank/percentile cohort: every attempt of the same kind, across all learners. */
    List<ExamAttempt> findByCourseIdAndExamTypeAndDifficulty(
            String courseId, ExamType examType, ExamDifficulty difficulty);
}
