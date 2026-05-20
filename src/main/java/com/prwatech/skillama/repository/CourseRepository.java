package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.Course;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends MongoRepository<Course, String> {
    Optional<Course> findByIsGuestCourseTrue();
    List<Course> findByIsPublicTrue();
    Optional<Course> findFirstByIsPublicTrue();
}
