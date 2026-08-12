package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CourseShareEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CourseShareEventRepository extends MongoRepository<CourseShareEvent, String> {
    boolean existsByUserIdAndCourseIdAndPlatform(String userId, String courseId, String platform);

    List<CourseShareEvent> findByUserIdOrderByCreatedAtDesc(String userId);
}
