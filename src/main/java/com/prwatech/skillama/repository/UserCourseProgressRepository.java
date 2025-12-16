package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.UserCourseProgress;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCourseProgressRepository extends MongoRepository<UserCourseProgress, String> {
    Optional<UserCourseProgress> findByUserIdAndCourseId(String userId, String courseId);
    List<UserCourseProgress> findByUserId(String userId);
}

