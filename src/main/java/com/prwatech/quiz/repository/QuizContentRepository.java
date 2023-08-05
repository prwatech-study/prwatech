package com.prwatech.quiz.repository;

import com.prwatech.quiz.model.QuizContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@EnableMongoRepositories
@Repository
public interface QuizContentRepository extends MongoRepository<QuizContent, String> {
}
