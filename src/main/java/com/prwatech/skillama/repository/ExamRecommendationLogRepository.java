package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.ExamRecommendationLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamRecommendationLogRepository extends MongoRepository<ExamRecommendationLog, String> {

    Long deleteByUserId(String userId);
}
