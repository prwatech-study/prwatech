package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CourseKnowledgeFile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CourseKnowledgeFileRepository extends MongoRepository<CourseKnowledgeFile, String> {
    List<CourseKnowledgeFile> findByCourseIdAndDeletedAtIsNullOrderByUploadedAtDesc(String courseId);

    Optional<CourseKnowledgeFile> findByIdAndCourseIdAndDeletedAtIsNull(String id, String courseId);
}
