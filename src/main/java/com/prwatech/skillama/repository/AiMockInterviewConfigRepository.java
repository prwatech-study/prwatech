package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.AiMockInterviewConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AiMockInterviewConfigRepository extends MongoRepository<AiMockInterviewConfig, String> {
    List<AiMockInterviewConfig> findByActiveTrueOrderBySortOrderAsc();
}
