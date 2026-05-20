package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.ConsumeQueryRequestDTO;
import com.prwatech.skillama.dto.FreemiumRegisterRequestDTO;
import com.prwatech.skillama.dto.FreemiumStatusDTO;
import com.prwatech.skillama.dto.QueryCreditsDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.QueryActivityLog;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.QueryActivityLogRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FreemiumService {

    public static final int FREEMIUM_QUERY_LIMIT = 20;
    public static final int REFERRAL_QUERY_BONUS = 10;
    public static final List<String> FREEMIUM_MODULES = Arrays.asList("Ai-Tutor", "Code Execution");
    public static final List<String> FREEMIUM_REFERRAL_MODULES = Arrays.asList("Ai-Tutor", "Code Execution", "Debug");
    public static final List<String> PREMIUM_MODULES = Arrays.asList(
            "Ai-Tutor", "Code Execution", "Debug", "Courses", "Curriculum");

    private final SkillamaUserRepository userRepository;
    private final QueryActivityLogRepository queryActivityLogRepository;
    private final PasswordEncode passwordEncode;
    private final UserCourseAccessService userCourseAccessService;

    public FreemiumStatusDTO getStatus(String userId) {
        User user = requireUser(userId);
        return toStatusDto(user);
    }

    public QueryCreditsDTO getQueryCredits(User user) {
        if (isUnlimited(user)) {
            return QueryCreditsDTO.builder().used(user.getQueryCreditsUsed()).limit(null).build();
        }
        int limit = effectiveLimit(user);
        int used = user.getQueryCreditsUsed() != null ? user.getQueryCreditsUsed() : 0;
        return QueryCreditsDTO.builder().used(used).limit(limit).build();
    }

    @Transactional
    public FreemiumStatusDTO consumeQuery(String userId, ConsumeQueryRequestDTO request) {
        User user = requireUser(userId);
        if (!isUnlimited(user)) {
            int limit = effectiveLimit(user);
            int used = user.getQueryCreditsUsed() != null ? user.getQueryCreditsUsed() : 0;
            if (used >= limit) {
                throw new IllegalStateException("Query credit limit reached");
            }
            user.setQueryCreditsUsed(used + 1);
            user.setUpdatedAt(LocalDateTime.now());
            user = userRepository.save(user);
        }

        queryActivityLogRepository.save(QueryActivityLog.builder()
                .userId(userId)
                .queryType(request != null && request.getQueryType() != null ? request.getQueryType() : "CHAT")
                .courseId(request != null ? request.getCourseId() : null)
                .createdAt(LocalDateTime.now())
                .build());

        return toStatusDto(user);
    }

    @Transactional
    public FreemiumStatusDTO applyReferral(String userId, String code) {
        User user = requireUser(userId);
        if (user.getReferredBy() != null) {
            throw new IllegalStateException("Referral already applied");
        }
        User referrer = userRepository.findByReferralCode(normalizeReferralCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Invalid referral code"));
        if (referrer.getId().equals(userId)) {
            throw new IllegalArgumentException("Cannot use your own referral code");
        }

        user.setReferredBy(referrer.getReferralCode());
        applyReferralBenefits(user);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return toStatusDto(user);
    }

    public String getReferralCode(String userId) {
        User user = requireUser(userId);
        if (user.getReferralCode() == null) {
            user.setReferralCode(generateReferralCode());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }
        return user.getReferralCode();
    }

    @Transactional
    public User registerFreemiumUser(FreemiumRegisterRequestDTO request) {
        validatePhone(request.getPhone());
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalStateException("Email is already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(normalizePhone(request.getPhone()));
        user.setEmailVerified(true);
        user.setActive(true);
        user.setRole(User.UserRole.USER);
        user.setPlanTier(User.PlanTier.FREEMIUM);
        user.setQueryCreditsUsed(0);
        user.setQueryCreditsLimit(FREEMIUM_QUERY_LIMIT);
        user.setEnabledModules(new ArrayList<>(FREEMIUM_MODULES));
        user.setReferralCode(generateReferralCode());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncode.getEncryptedPassword(request.getPassword()));
        }

        if (request.getReferralCode() != null && !request.getReferralCode().isBlank()) {
            userRepository.findByReferralCode(normalizeReferralCode(request.getReferralCode()))
                    .ifPresent(referrer -> {
                        if (!referrer.getEmail().equalsIgnoreCase(request.getEmail())) {
                            user.setReferredBy(referrer.getReferralCode());
                            applyReferralBenefits(user);
                        }
                    });
        }

        User saved = userRepository.save(user);
        userCourseAccessService.enrollDefaultFreemiumCourse(saved.getId());
        return saved;
    }

    public void validateEligibleForFreemiumMigration(User user) {
        if (user.getPlanTier() == User.PlanTier.FREEMIUM) {
            throw new IllegalStateException("Account is already on the freemium plan");
        }
        if (user.getPlanTier() == User.PlanTier.PAID || user.getPlanTier() == User.PlanTier.ENTERPRISE) {
            throw new IllegalStateException("Premium accounts cannot be migrated via OTP. Contact admin.");
        }
    }

    @Transactional
    public FreemiumStatusDTO migrateLegacyUserToFreemium(String email, String phone) {
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        validateEligibleForFreemiumMigration(user);
        applyFreemiumPlan(user, phone);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        userCourseAccessService.enrollDefaultFreemiumCourse(user.getId());
        return toStatusDto(user);
    }

    @Transactional
    public FreemiumStatusDTO updateUserPlan(String userId, User.PlanTier planTier) {
        if (planTier == null) {
            throw new IllegalArgumentException("planTier is required");
        }
        User user = requireUser(userId);
        switch (planTier) {
            case FREEMIUM:
                applyFreemiumPlan(user, user.getPhone());
                break;
            case PAID:
            case ENTERPRISE:
                applyPremiumPlan(user, planTier);
                break;
            default:
                throw new IllegalArgumentException("Unsupported plan tier: " + planTier);
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return toStatusDto(user);
    }

    private void applyFreemiumPlan(User user, String phone) {
        if (phone != null && !phone.isBlank()) {
            user.setPhone(normalizePhone(phone));
        } else if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone is required to move to freemium plan");
        }
        user.setPlanTier(User.PlanTier.FREEMIUM);
        if (user.getQueryCreditsUsed() == null) {
            user.setQueryCreditsUsed(0);
        }
        if (hasReferralBonus(user)) {
            applyReferralBenefits(user);
        } else {
            user.setQueryCreditsLimit(FREEMIUM_QUERY_LIMIT);
            user.setEnabledModules(new ArrayList<>(FREEMIUM_MODULES));
        }
        if (user.getReferralCode() == null) {
            user.setReferralCode(generateReferralCode());
        }
        if (user.getEmailVerified() == null) {
            user.setEmailVerified(true);
        }
    }

    private void applyPremiumPlan(User user, User.PlanTier tier) {
        user.setPlanTier(tier);
        user.setQueryCreditsLimit(null);
        user.setEnabledModules(new ArrayList<>(PREMIUM_MODULES));
    }

    public void initializeFreemiumDefaults(User user) {
        if (user.getPlanTier() == null) {
            user.setPlanTier(User.PlanTier.FREEMIUM);
        }
        if (user.getQueryCreditsLimit() == null && user.getPlanTier() == User.PlanTier.FREEMIUM) {
            user.setQueryCreditsLimit(FREEMIUM_QUERY_LIMIT);
        }
        if (user.getQueryCreditsUsed() == null) {
            user.setQueryCreditsUsed(0);
        }
        if (user.getEnabledModules() == null || user.getEnabledModules().isEmpty()) {
            user.setEnabledModules(new ArrayList<>(hasReferralBonus(user) ? FREEMIUM_REFERRAL_MODULES : FREEMIUM_MODULES));
        }
        if (user.getReferralCode() == null) {
            user.setReferralCode(generateReferralCode());
        }
    }

    /**
     * Pre-freemium accounts have no planTier — preserve prior unlimited feature access.
     */
    public boolean isLegacyUser(User user) {
        return user != null && user.getPlanTier() == null;
    }

    public boolean isUnlimited(User user) {
        return isLegacyUser(user)
                || user.getPlanTier() == User.PlanTier.PAID
                || user.getPlanTier() == User.PlanTier.ENTERPRISE;
    }

    public boolean hasModule(User user, String moduleName) {
        if (isUnlimited(user)) {
            return true;
        }
        List<String> modules = user.getEnabledModules();
        return modules != null && modules.stream().anyMatch(m -> m.equalsIgnoreCase(moduleName));
    }

    public int remainingQueries(User user) {
        if (isUnlimited(user)) {
            return Integer.MAX_VALUE;
        }
        int limit = effectiveLimit(user);
        int used = user.getQueryCreditsUsed() != null ? user.getQueryCreditsUsed() : 0;
        return Math.max(0, limit - used);
    }

    private int effectiveLimit(User user) {
        if (user.getQueryCreditsLimit() != null) {
            return user.getQueryCreditsLimit();
        }
        return hasReferralBonus(user) ? FREEMIUM_QUERY_LIMIT + REFERRAL_QUERY_BONUS : FREEMIUM_QUERY_LIMIT;
    }

    private boolean hasReferralBonus(User user) {
        return user.getReferredBy() != null && !user.getReferredBy().isBlank();
    }

    private void applyReferralBenefits(User user) {
        user.setQueryCreditsLimit(FREEMIUM_QUERY_LIMIT + REFERRAL_QUERY_BONUS);
        user.setEnabledModules(new ArrayList<>(FREEMIUM_REFERRAL_MODULES));
    }

    private FreemiumStatusDTO toStatusDto(User user) {
        return FreemiumStatusDTO.builder()
                .planTier(user.getPlanTier() != null ? user.getPlanTier() : User.PlanTier.FREEMIUM)
                .phone(user.getPhone())
                .emailVerified(user.getEmailVerified())
                .referralCode(user.getReferralCode())
                .referredBy(user.getReferredBy())
                .queryCreditsUsed(user.getQueryCreditsUsed())
                .queryCreditsLimit(isUnlimited(user) ? null : effectiveLimit(user))
                .enabledModules(user.getEnabledModules())
                .build();
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public static String generateReferralCode() {
        return "SKILL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static String normalizeReferralCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    public static void validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.length() < 10) {
            throw new IllegalArgumentException("Invalid phone number");
        }
    }

    public static String normalizePhone(String phone) {
        String trimmed = phone.trim();
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        if (trimmed.length() == 10) {
            return "+91" + trimmed;
        }
        return trimmed;
    }

}
