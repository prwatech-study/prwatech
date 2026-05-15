package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.EmailOtp;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmailOtpRepository extends MongoRepository<EmailOtp, String> {
    Optional<EmailOtp> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, EmailOtp.OtpPurpose purpose);

    Optional<EmailOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    Optional<EmailOtp> findByVerificationToken(String verificationToken);
}
