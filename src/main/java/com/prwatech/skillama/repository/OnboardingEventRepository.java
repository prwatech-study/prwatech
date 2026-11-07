package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.OnboardingEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OnboardingEventRepository extends MongoRepository<OnboardingEvent, String> {
    Optional<OnboardingEvent> findByReferralCode(String referralCode);

    List<OnboardingEvent> findAllByOrderByCreatedAtDesc();

    List<OnboardingEvent> findByStatusNot(OnboardingEvent.Status status);
}
