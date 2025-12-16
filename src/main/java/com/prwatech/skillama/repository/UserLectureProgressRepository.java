package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.UserLectureProgress;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserLectureProgressRepository extends MongoRepository<UserLectureProgress, String> {
    Optional<UserLectureProgress> findByUserIdAndCourseIdAndLectureId(
        String userId, String courseId, String lectureId);
    List<UserLectureProgress> findByUserIdAndCourseId(String userId, String courseId);
    long countByUserIdAndCourseIdAndCompleted(String userId, String courseId, boolean completed);
}

