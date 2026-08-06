package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CourseShareEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CourseShareEventRepository extends MongoRepository<CourseShareEvent, String> {
    boolean existsByUserIdAndCourseIdAndPlatform(String userId, String courseId, String platform);
}
