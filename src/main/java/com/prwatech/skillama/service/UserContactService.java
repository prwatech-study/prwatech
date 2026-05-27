package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.ContactAvailabilityDTO;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Enforces unique email, unique phone, and unique (email + phone) pairs across learner accounts.
 */
@Service
@RequiredArgsConstructor
public class UserContactService {

    private final SkillamaUserRepository userRepository;

    public String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    /**
     * @param excludeUserId current user when updating profile or completing signup for existing row
     */
    public void assertContactUnique(String email, String phone, String excludeUserId) {
        String normEmail = normalizeEmail(email);
        String normPhone =
                phone != null && !phone.isBlank() ? FreemiumService.normalizePhone(phone) : null;

        if (normEmail != null) {
            findByEmail(normEmail).ifPresent(owner -> {
                if (!isSameUser(owner, excludeUserId)) {
                    throw new IllegalStateException(
                            "This email is already registered. Please sign in instead.");
                }
            });
        }

        if (normPhone != null) {
            findByPhone(normPhone).ifPresent(owner -> {
                if (!isSameUser(owner, excludeUserId)) {
                    throw new IllegalStateException(
                            "This mobile number is already linked to another account.");
                }
            });
        }

        if (normEmail != null && normPhone != null) {
            userRepository.findByEmailAndPhone(normEmail, normPhone).ifPresent(owner -> {
                if (!isSameUser(owner, excludeUserId)) {
                    throw new IllegalStateException(
                            "An account with this email and mobile number already exists.");
                }
            });
        }
    }

    public ContactAvailabilityDTO checkAvailability(String email, String phone, String excludeUserId) {
        String normEmail = normalizeEmail(email);
        String normPhone =
                phone != null && !phone.isBlank() ? FreemiumService.normalizePhone(phone) : null;

        boolean emailAvailable = true;
        boolean phoneAvailable = true;
        boolean comboAvailable = true;
        StringBuilder message = new StringBuilder();

        if (normEmail != null) {
            Optional<User> byEmail = findByEmail(normEmail);
            if (byEmail.isPresent() && !isSameUser(byEmail.get(), excludeUserId)) {
                emailAvailable = false;
                message.append("Email is already registered. ");
            }
        }

        if (normPhone != null) {
            Optional<User> byPhone = findByPhone(normPhone);
            if (byPhone.isPresent() && !isSameUser(byPhone.get(), excludeUserId)) {
                phoneAvailable = false;
                message.append("Mobile number is already in use. ");
            }
        }

        if (normEmail != null && normPhone != null) {
            Optional<User> byCombo = userRepository.findByEmailAndPhone(normEmail, normPhone);
            if (byCombo.isPresent() && !isSameUser(byCombo.get(), excludeUserId)) {
                comboAvailable = false;
                if (emailAvailable && phoneAvailable) {
                    message.append("This email and mobile combination is already registered. ");
                }
            }
        }

        String msg = message.toString().trim();
        if (msg.isEmpty()) {
            msg = "Email and mobile are available.";
        }

        return ContactAvailabilityDTO.builder()
                .emailAvailable(emailAvailable)
                .phoneAvailable(phoneAvailable)
                .comboAvailable(comboAvailable && emailAvailable && phoneAvailable)
                .message(msg)
                .build();
    }

    /**
     * Signup OTP: block taken email/phone unless email belongs to an existing account being upgraded.
     */
    public String resolveExcludeUserIdForSignupOtp(String email) {
        return findByEmail(normalizeEmail(email)).map(User::getId).orElse(null);
    }

    public boolean isEmailBlockedForNewSignup(String email) {
        String normEmail = normalizeEmail(email);
        if (normEmail == null) {
            return false;
        }
        Optional<User> existing = findByEmail(normEmail);
        if (existing.isEmpty()) {
            return false;
        }
        User user = existing.get();
        if (user.getPlanTier() == User.PlanTier.PAID || user.getPlanTier() == User.PlanTier.ENTERPRISE) {
            return true;
        }
        return user.getPlanTier() == User.PlanTier.FREEMIUM && user.isActive();
    }

    private Optional<User> findByEmail(String normEmail) {
        if (normEmail == null) {
            return Optional.empty();
        }
        Optional<User> exact = userRepository.findByEmail(normEmail);
        if (exact.isPresent()) {
            return exact;
        }
        return userRepository.findByEmailIgnoreCase(normEmail);
    }

    private Optional<User> findByPhone(String normPhone) {
        if (normPhone == null) {
            return Optional.empty();
        }
        Optional<User> direct = userRepository.findByPhone(normPhone);
        if (direct.isPresent()) {
            return direct;
        }
        String digits = normPhone.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            String last10 = digits.substring(digits.length() - 10);
            return userRepository.findByPhoneEndingWith(last10);
        }
        return Optional.empty();
    }

    private static boolean isSameUser(User user, String excludeUserId) {
        return excludeUserId != null && excludeUserId.equals(user.getId());
    }
}
