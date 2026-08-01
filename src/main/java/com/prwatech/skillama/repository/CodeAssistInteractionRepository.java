package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CodeAssistInteraction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CodeAssistInteractionRepository extends MongoRepository<CodeAssistInteraction, String> {
    List<CodeAssistInteraction> findByUserIdOrderByCreatedAtDesc(String userId);
}
