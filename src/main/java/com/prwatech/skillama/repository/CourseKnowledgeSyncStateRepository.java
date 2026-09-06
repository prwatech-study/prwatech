package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CourseKnowledgeSyncState;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CourseKnowledgeSyncStateRepository extends MongoRepository<CourseKnowledgeSyncState, String> {
    Optional<CourseKnowledgeSyncState> findByCourseId(String courseId);
}
