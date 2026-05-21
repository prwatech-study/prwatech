package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.ResetPasswordRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.EmailOtp;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final SkillamaUserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncode passwordEncode;

    public void sendForgotPasswordOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalized = email.trim().toLowerCase();
        if (userRepository.findByEmail(normalized).isPresent()) {
            otpService.sendOtp(normalized, EmailOtp.OtpPurpose.PASSWORD_RESET);
        }
        // Always succeed to avoid email enumeration
    }

    @Transactional
    public void resetPassword(ResetPasswordRequestDTO request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }
        String normalized = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getVerificationToken() != null && !request.getVerificationToken().isBlank()) {
            otpService.validateVerificationToken(
                    normalized, request.getVerificationToken(), EmailOtp.OtpPurpose.PASSWORD_RESET);
        } else if (request.getOtp() != null && !request.getOtp().isBlank()) {
            otpService.verifyOtp(normalized, request.getOtp(), EmailOtp.OtpPurpose.PASSWORD_RESET);
        } else {
            throw new IllegalArgumentException("OTP or verificationToken is required");
        }

        user.setPassword(passwordEncode.getEncryptedPassword(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}
