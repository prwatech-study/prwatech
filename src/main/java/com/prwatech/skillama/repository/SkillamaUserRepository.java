package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillamaUserRepository extends MongoRepository<User, String>, SkillamaUserRepositoryCustom {
    java.util.Optional<User> findByReferralCode(String referralCode);
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    long countByLmsThemePreference(String lmsThemePreference);

    Optional<User> findByPhone(String phone);

    /** Match last 10 digits for legacy rows stored without country code. */
    Optional<User> findByPhoneEndingWith(String lastTenDigits);

    Optional<User> findByEmailAndPhone(String email, String phone);

    /** Pre-freemium accounts (planTier not set) — candidates for admin backfill. */
    List<User> findByPlanTierIsNull();
}
