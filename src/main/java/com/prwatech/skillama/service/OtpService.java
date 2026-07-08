package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.dto.OtpVerifyResponseDTO;
import com.prwatech.skillama.model.EmailOtp;
import com.prwatech.skillama.repository.EmailOtpRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import com.prwatech.skillama.util.EmailValidation;
import com.prwatech.skillama.util.IndiaTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int VERIFICATION_TOKEN_EXPIRY_MINUTES = 30;

    private final EmailOtpRepository emailOtpRepository;
    private final EmailServiceImpl emailService;
    private final PasswordEncode passwordEncode;
    private final UserService userService;
    private final UserContactService userContactService;

    @Transactional
    public void sendOtp(String email, EmailOtp.OtpPurpose purpose) {
        EmailValidation.assertValidFormat(email);
        if (purpose == null) {
            purpose = EmailOtp.OtpPurpose.SIGNUP;
        }

        String otp = generateOtp();
        String otpHash = passwordEncode.getEncryptedPassword(otp);

        EmailOtp record = EmailOtp.builder()
                .email(email.trim().toLowerCase())
                .otpHash(otpHash)
                .purpose(purpose)
                .expiresAt(IndiaTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .createdAt(IndiaTime.now())
                .build();
        emailOtpRepository.save(record);

        try {
            emailService.sendEmail(new EmailSendDto(
                    email,
                    "Skillama verification code",
                    "Your verification code is: " + otp + "\n\nThis code expires in " + OTP_EXPIRY_MINUTES + " minutes."));
        } catch (Exception e) {
            LOGGER.warn("Failed to send OTP email to {}: {}", email, e.getMessage());
            LOGGER.info("DEV OTP for {}: {}", email, otp);
        }
    }

    @Transactional
    public OtpVerifyResponseDTO verifyOtp(String email, String otp) {
        return verifyOtp(email, otp, null);
    }

    @Transactional
    public OtpVerifyResponseDTO verifyOtp(String email, String otp, EmailOtp.OtpPurpose expectedPurpose) {
        if (email == null || otp == null) {
            throw new IllegalArgumentException("Email and OTP are required");
        }

        String normalizedEmail = email.trim().toLowerCase();
        EmailOtp record;
        if (expectedPurpose != null) {
            record = emailOtpRepository
                    .findTopByEmailAndPurposeOrderByCreatedAtDesc(normalizedEmail, expectedPurpose)
                    .orElseThrow(() -> new IllegalArgumentException("No OTP found for this email and purpose"));
        } else {
            record = emailOtpRepository
                    .findTopByEmailOrderByCreatedAtDesc(normalizedEmail)
                    .orElseThrow(() -> new IllegalArgumentException("No OTP found for this email"));
        }

        if (record.getExpiresAt().isBefore(IndiaTime.now())) {
            throw new IllegalArgumentException("OTP has expired");
        }
        if (!passwordEncode.compare(otp, record.getOtpHash())) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        String verificationToken = UUID.randomUUID().toString();
        record.setVerificationToken(verificationToken);
        record.setExpiresAt(IndiaTime.now().plusMinutes(VERIFICATION_TOKEN_EXPIRY_MINUTES));
        emailOtpRepository.save(record);

        return OtpVerifyResponseDTO.builder().verificationToken(verificationToken).build();
    }

    public void validateVerificationToken(String email, String verificationToken) {
        validateVerificationToken(email, verificationToken, null);
    }

    public void validateVerificationToken(String email, String verificationToken, EmailOtp.OtpPurpose expectedPurpose) {
        EmailOtp record = emailOtpRepository.findByVerificationToken(verificationToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (!record.getEmail().equalsIgnoreCase(email.trim())) {
            throw new IllegalArgumentException("Verification token does not match email");
        }
        if (expectedPurpose != null && record.getPurpose() != expectedPurpose) {
            throw new IllegalArgumentException("Verification token purpose mismatch");
        }
        if (record.getExpiresAt().isBefore(IndiaTime.now())) {
            throw new IllegalArgumentException("Verification token has expired");
        }
    }

    /**
     * Validates email/phone for signup OTP. {@code phone} may be null until the client sends it.
     */
    public void assertSignupContactAvailable(String email, String phone) {
        String normEmail = userContactService.normalizeEmail(email);
        if (userContactService.isEmailBlockedForNewSignup(normEmail)) {
            throw new IllegalStateException("Email is already in use. Please sign in.");
        }
        String excludeUserId = userContactService.resolveExcludeUserIdForSignupOtp(normEmail);
        if (phone != null && !phone.isBlank()) {
            FreemiumService.validatePhone(phone);
            userContactService.assertContactUnique(normEmail, phone, excludeUserId);
        } else if (excludeUserId == null) {
            userContactService.assertContactUnique(normEmail, null, null);
        }
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int value = 100000 + random.nextInt(900000);
        return String.valueOf(value);
    }
}
