package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillamaUserRepository extends MongoRepository<User, String>, SkillamaUserRepositoryCustom {
    java.util.Optional<User> findByReferralCode(String referralCode);
    Optional<User> findByEmail(String email);
}
