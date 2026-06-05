package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CourseStudyMaterial;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CourseStudyMaterialRepository extends MongoRepository<CourseStudyMaterial, String> {
    List<CourseStudyMaterial> findByCourseIdOrderBySortOrderAscUploadedAtAsc(String courseId);

    void deleteByCourseId(String courseId);
}
