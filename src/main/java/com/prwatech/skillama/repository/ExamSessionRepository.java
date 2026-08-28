package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.ExamSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExamSessionRepository extends MongoRepository<ExamSession, String> {
    Optional<ExamSession> findByExamSessionId(String examSessionId);

    Long deleteByUserId(String userId);
}
