package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.AiInterviewSchedule;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiInterviewScheduleRepository extends MongoRepository<AiInterviewSchedule, String> {
    Optional<AiInterviewSchedule> findByInviteToken(String inviteToken);

    List<AiInterviewSchedule> findAllByOrderByScheduledAtDesc();

    List<AiInterviewSchedule> findByStatusOrderByScheduledAtDesc(String status);

    List<AiInterviewSchedule> findByCandidateEmailOrderByScheduledAtDesc(String candidateEmail);

    List<AiInterviewSchedule> findByCandidateEmailAndStatusIn(String candidateEmail, Collection<String> statuses);
}
