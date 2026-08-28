package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.AiAnswerFeedback;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiAnswerFeedbackRepository extends MongoRepository<AiAnswerFeedback, String> {
    Optional<AiAnswerFeedback> findByUserIdAndMessageId(String userId, String messageId);

    List<AiAnswerFeedback> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByHelpful(boolean helpful);

    Long deleteByUserId(String userId);
}
