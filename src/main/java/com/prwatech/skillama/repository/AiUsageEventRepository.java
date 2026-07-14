package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.AiUsageEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AiUsageEventRepository extends MongoRepository<AiUsageEvent, String> {
    List<AiUsageEvent> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String userId, LocalDateTime start, LocalDateTime end);

    List<AiUsageEvent> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
