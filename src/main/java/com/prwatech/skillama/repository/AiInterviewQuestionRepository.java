package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.AiInterviewQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AiInterviewQuestionRepository extends MongoRepository<AiInterviewQuestion, String> {
    List<AiInterviewQuestion> findAllByOrderByCreatedAtDesc();
}
