package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.AiMockInterviewSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AiMockInterviewSessionRepository extends MongoRepository<AiMockInterviewSession, String> {
    List<AiMockInterviewSession> findByUserIdOrderByStartedAtDesc(String userId);

    List<AiMockInterviewSession> findAllByOrderByStartedAtDesc();

    List<AiMockInterviewSession> findByStatusOrderByStartedAtDesc(String status);
}
