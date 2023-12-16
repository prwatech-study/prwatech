package com.prwatech.quiz.repository;

import com.prwatech.quiz.model.QuizContentAttemptMap;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface QuizAttemptRepository extends MongoRepository<QuizContentAttemptMap, String> {
}
