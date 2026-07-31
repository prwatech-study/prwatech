package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.Doubt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DoubtRepository extends MongoRepository<Doubt, String> {
    List<Doubt> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Doubt> findByUserIdAndCourseIdOrderByCreatedAtDesc(String userId, String courseId);
}
