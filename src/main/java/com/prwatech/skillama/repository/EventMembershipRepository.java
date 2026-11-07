package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.EventMembership;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EventMembershipRepository extends MongoRepository<EventMembership, String> {
    Optional<EventMembership> findByUserId(String userId);

    List<EventMembership> findByEventId(String eventId);

    List<EventMembership> findByEventIdAndStatus(String eventId, EventMembership.Status status);

    long countByEventId(String eventId);

    long countByEventIdAndStatus(String eventId, EventMembership.Status status);

    List<EventMembership> findByStatus(EventMembership.Status status);
}
