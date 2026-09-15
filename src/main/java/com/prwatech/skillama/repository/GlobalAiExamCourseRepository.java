package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.GlobalAiExamCourse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlobalAiExamCourseRepository extends MongoRepository<GlobalAiExamCourse, String> {
    boolean existsByCourseId(String courseId);

    Optional<GlobalAiExamCourse> findByCourseId(String courseId);

    boolean existsByNameKey(String nameKey);

    Optional<GlobalAiExamCourse> findByNameKey(String nameKey);
}
