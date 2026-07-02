package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.ModuleQuizSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModuleQuizSessionRepository extends MongoRepository<ModuleQuizSession, String> {
    Optional<ModuleQuizSession> findByQuizSessionId(String quizSessionId);
}
