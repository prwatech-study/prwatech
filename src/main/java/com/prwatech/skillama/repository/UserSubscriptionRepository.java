package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.UserSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends MongoRepository<UserSubscription, String> {
    Optional<UserSubscription> findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
            String userId, UserSubscription.SubscriptionStatus status);

    List<UserSubscription> findByUserIdOrderByCreatedAtDesc(String userId);
}
