package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CourseKnowledgeSyncEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CourseKnowledgeSyncEventRepository extends MongoRepository<CourseKnowledgeSyncEvent, String> {
    Page<CourseKnowledgeSyncEvent> findByCourseIdOrderByTriggeredAtDesc(String courseId, Pageable pageable);

    Optional<CourseKnowledgeSyncEvent> findByCourseIdAndIngestionJobId(String courseId, String ingestionJobId);
}
