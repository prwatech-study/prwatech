package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.ContactAvailabilityDTO;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            findConflictingEmailOwner(normEmail, excludeUserId).ifPresent(owner -> {
                throw new IllegalStateException(
                        "This email is already registered. Please sign in instead.");
            });
        }

        if (normPhone != null) {
            findConflictingPhoneOwner(normPhone, excludeUserId).ifPresent(owner -> {
                throw new IllegalStateException(
                        "This mobile number is already linked to another account.");
            });
        }

        if (normEmail != null && normPhone != null) {
            findConflictingComboOwner(normEmail, normPhone, excludeUserId).ifPresent(owner -> {
                throw new IllegalStateException(
                        "An account with this email and mobile number already exists.");
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
            if (findConflictingEmailOwner(normEmail, excludeUserId).isPresent()) {
                emailAvailable = false;
                message.append("Email is already registered. ");
            }
        }

        if (normPhone != null) {
            if (findConflictingPhoneOwner(normPhone, excludeUserId).isPresent()) {
                phoneAvailable = false;
                message.append("Mobile number is already in use. ");
            }
        }

        if (normEmail != null && normPhone != null) {
            if (findConflictingComboOwner(normEmail, normPhone, excludeUserId).isPresent()) {
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

    private Optional<User> findConflictingEmailOwner(String normEmail, String excludeUserId) {
        return findByEmail(normEmail)
                .filter(user -> !isSameUser(user, excludeUserId));
    }

    private Optional<User> findConflictingPhoneOwner(String normPhone, String excludeUserId) {
        return findUsersByNormalizedPhone(normPhone).stream()
                .filter(user -> !isSameUser(user, excludeUserId))
                .findFirst();
    }

    private Optional<User> findConflictingComboOwner(
            String normEmail, String normPhone, String excludeUserId) {
        return findUsersByEmailAndPhone(normEmail, normPhone).stream()
                .filter(user -> !isSameUser(user, excludeUserId))
                .findFirst();
    }

    private List<User> findUsersByNormalizedPhone(String normPhone) {
        if (normPhone == null) {
            return List.of();
        }
        Map<String, User> byId = new LinkedHashMap<>();
        for (User user : userRepository.findAllByPhone(normPhone)) {
            if (user.getId() != null) {
                byId.put(user.getId(), user);
            }
        }
        String digits = normPhone.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            String last10 = digits.substring(digits.length() - 10);
            for (User user : userRepository.findAllByPhoneEndingWith(last10)) {
                if (user.getId() != null) {
                    byId.putIfAbsent(user.getId(), user);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<User> findUsersByEmailAndPhone(String normEmail, String normPhone) {
        if (normEmail == null || normPhone == null) {
            return List.of();
        }
        Map<String, User> byId = new LinkedHashMap<>();
        for (User user : userRepository.findAllByEmailAndPhone(normEmail, normPhone)) {
            if (user.getId() != null) {
                byId.put(user.getId(), user);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static boolean isSameUser(User user, String excludeUserId) {
        return excludeUserId != null && excludeUserId.equals(user.getId());
    }
}
