package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.outreach.OutreachRecipient;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OutreachRecipientRepository extends MongoRepository<OutreachRecipient, String> {
    Optional<OutreachRecipient> findByTrackingToken(String trackingToken);

    Optional<OutreachRecipient> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    long countByLifecycle(OutreachRecipient.Lifecycle lifecycle);
}