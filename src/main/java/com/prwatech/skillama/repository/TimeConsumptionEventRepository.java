package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.TimeConsumptionEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TimeConsumptionEventRepository extends MongoRepository<TimeConsumptionEvent, String> {
    List<TimeConsumptionEvent> findByUserIdOrderByCreatedAtDesc(String userId);

    List<TimeConsumptionEvent> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
