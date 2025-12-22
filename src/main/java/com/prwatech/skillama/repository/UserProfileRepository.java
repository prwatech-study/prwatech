package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends MongoRepository<UserProfile, String> {
    Optional<UserProfile> findBySessionId(String sessionId);
    Optional<UserProfile> findByUserId(String userId);
    Optional<UserProfile> findByUserIdOrSessionId(String userId, String sessionId);
    void deleteBySessionId(String sessionId);
}

