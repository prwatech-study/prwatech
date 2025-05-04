package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CourseCurriculum;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseCurriculumRepository extends MongoRepository<CourseCurriculum, String> {
    List<CourseCurriculum> findByCourseIdOrderByOrderAsc(String courseId);
}
