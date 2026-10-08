package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.AiInterviewSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiInterviewSessionRepository extends MongoRepository<AiInterviewSession, String> {
    Optional<AiInterviewSession> findByScheduleId(String scheduleId);

    Optional<AiInterviewSession> findBySessionToken(String sessionToken);

    List<AiInterviewSession> findByStatus(String status);

    List<AiInterviewSession> findByStatusAndCompletedAtBetween(String status, Instant from, Instant to);
}
