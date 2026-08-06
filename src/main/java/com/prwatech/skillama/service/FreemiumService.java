package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.skillama.dto.AiBudgetDTO;
import com.prwatech.skillama.dto.ConsumeQueryRequestDTO;
import com.prwatech.skillama.dto.CreditAdjustRequestDTO;
import com.prwatech.skillama.dto.FreemiumCourseOptionDTO;
import com.prwatech.skillama.dto.FreemiumOfferingDTO;
import com.prwatech.skillama.dto.FreemiumRegisterRequestDTO;
import com.prwatech.skillama.dto.FreemiumStatusDTO;
import com.prwatech.skillama.dto.LegacyFreemiumBackfillResultDTO;
import com.prwatech.skillama.dto.WalletAdjustResultDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.QueryActivityLog;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.QueryActivityLogRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prwatech.skillama.util.IndiaTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FreemiumService {

    /**
     * Permanent USD reward credited to the REFERRER for every successful referral signup.
     * Stacks without limit; the referee gets no bonus.
     */
    public static final double REFERRER_REWARD_USD = 0.25;
    public static final String REFERRAL_BONUS_MODULE = "Debug";
    /** All freemium users get these modules from signup (referral rewards the referrer's wallet only). */
    public static final List<String> FREEMIUM_BASE_MODULES = Arrays.asList(
            "Ai-Tutor", "Code Execution", REFERRAL_BONUS_MODULE);
    public static final List<String> FREEMIUM_REFERRAL_MODULES = FREEMIUM_BASE_MODULES;
    /** @deprecated use {@link #FREEMIUM_BASE_MODULES} or {@link #FREEMIUM_REFERRAL_MODULES} */
    @Deprecated
    public static final List<String> FREEMIUM_MODULES = FREEMIUM_REFERRAL_MODULES;
    public static final List<String> PREMIUM_MODULES = Arrays.asList(
            "Ai-Tutor", "Code Execution", "Debug", "Courses", "Curriculum");

    private final SkillamaUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final QueryActivityLogRepository queryActivityLogRepository;
    private final PasswordEncode passwordEncode;
    private final UserCourseAccessService userCourseAccessService;
    private final UserContactService userContactService;
    private final AiUsageService aiUsageService;

    /** Public read — home page banner, signup copy (no auth). */
    public FreemiumOfferingDTO getPublicOffering() {
        return FreemiumOfferingDTO.builder()
                .baseModules(new ArrayList<>(FREEMIUM_BASE_MODULES))
                .modulesWithReferral(new ArrayList<>(FREEMIUM_REFERRAL_MODULES))
                .referralBonusModule(REFERRAL_BONUS_MODULE)
                .referrerRewardUsd(REFERRER_REWARD_USD)
                .courseSelectionAtSignup(true)
                .build();
    }

    public List<FreemiumCourseOptionDTO> listSignupCourseOptions() {
        return courseRepository.findAll().stream()
                // Only courses available to learners: not archived, not admin-deactivated,
                // and explicitly curated for the registration picker (marketing-controlled subset).
                .filter(c -> c.getDeletedAt() == null
                        && !Boolean.FALSE.equals(c.getActive())
                        && !Boolean.FALSE.equals(c.getRegistrationEligible()))
                .sorted(Comparator.comparing(Course::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(c -> FreemiumCourseOptionDTO.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .build())
                .collect(Collectors.toList());
    }

    public FreemiumStatusDTO getStatus(String userId) {
        User user = requireUser(userId);
        if (user.getPlanTier() == User.PlanTier.FREEMIUM) {
            initializeFreemiumDefaults(user);
            user = userRepository.save(user);
        }
        return toStatusDto(user);
    }

    @Transactional
    public FreemiumStatusDTO consumeQuery(String userId, ConsumeQueryRequestDTO request) {
        User user = requireUser(userId);
        // The dollar AI wallet is the ONLY consumption limit — query counts are no longer metered.
        aiUsageService.assertWithinBudget(user);

        queryActivityLogRepository.save(QueryActivityLog.builder()
                .userId(userId)
                .queryType(request != null && request.getQueryType() != null ? request.getQueryType() : "CHAT")
                .courseId(request != null ? request.getCourseId() : null)
                .createdAt(IndiaTime.now())
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
        rewardReferrer(referrer);
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);
        return toStatusDto(user);
    }

    /**
     * Credits the REFERRER's permanent wallet bonus. The referee gets nothing — referral
     * rewards are referrer-only. Stacks without limit across repeat referrals.
     */
    private void rewardReferrer(User referrer) {
        double bonus = referrer.getReferralBonusUsd() != null ? referrer.getReferralBonusUsd() : 0.0;
        referrer.setReferralBonusUsd(bonus + REFERRER_REWARD_USD);
        referrer.setUpdatedAt(IndiaTime.now());
        // referrer is a DIFFERENT entity from the caller's `user` — it is not covered by any
        // outer save(user) call, so it must be persisted explicitly here.
        userRepository.save(referrer);
    }

    public String getReferralCode(String userId) {
        User user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ForbiddenException("Verify your email before viewing your referral code");
        }
        if (user.getReferralCode() == null) {
            user.setReferralCode(generateReferralCode());
            user.setUpdatedAt(IndiaTime.now());
            userRepository.save(user);
        }
        return user.getReferralCode();
    }

    @Transactional
    public User registerFreemiumUser(FreemiumRegisterRequestDTO request) {
        validatePhone(request.getPhone());
        String email = userContactService.normalizeEmail(request.getEmail());
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isEmpty()) {
            existing = userRepository.findByEmailIgnoreCase(email);
        }
        validateFreemiumCourseSelection(request, existing);
        if (existing.isPresent()) {
            return completeFreemiumRegistrationForExisting(existing.get(), request);
        }

        userContactService.assertContactUnique(email, request.getPhone(), null);

        User user = new User();
        user.setName(request.getName());
        user.setEmail(email);
        user.setPhone(normalizePhone(request.getPhone()));
        user.setEmailVerified(true);
        user.setActive(true);
        user.setRole(User.UserRole.USER);
        user.setPlanTier(User.PlanTier.FREEMIUM);
        user.setEnabledModules(new ArrayList<>(FREEMIUM_BASE_MODULES));
        user.setReferralCode(generateReferralCode());
        user.setCreatedAt(IndiaTime.now());
        user.setUpdatedAt(IndiaTime.now());

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncode.getEncryptedPassword(request.getPassword()));
        }

        applyReferralOnSignup(user, request);

        User saved = userRepository.save(user);
        userCourseAccessService.applyUserChosenFreemiumCourse(saved, request.getFreemiumCourseId());
        saved.setOnboardingCompleted(true);
        saved.setOnboardingCompletedAt(IndiaTime.now());
        return userRepository.save(saved);
    }

    /**
     * OTP-verified freemium signup for an email that already has an account.
     * Legacy or inactive users are upgraded; active freemium users are signed in.
     */
    private User completeFreemiumRegistrationForExisting(User user, FreemiumRegisterRequestDTO request) {
        if (user.getPlanTier() == User.PlanTier.PAID || user.getPlanTier() == User.PlanTier.ENTERPRISE) {
            throw new IllegalStateException(
                    "This email has a premium account. Use the login page or contact support.");
        }

        userContactService.assertContactUnique(
                userContactService.normalizeEmail(request.getEmail()),
                request.getPhone(),
                user.getId());

        if (user.getPlanTier() == null) {
            applyFreemiumPlan(user, request.getPhone());
        } else if (user.getPlanTier() == User.PlanTier.FREEMIUM) {
            user.setPhone(normalizePhone(request.getPhone()));
            initializeFreemiumDefaults(user);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }
        user.setEmailVerified(true);
        user.setActive(true);

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncode.getEncryptedPassword(request.getPassword()));
        }

        applyReferralOnSignup(user, request);

        user.setUpdatedAt(IndiaTime.now());
        User saved = userRepository.save(user);
        userCourseAccessService.applyUserChosenFreemiumCourse(saved, request.getFreemiumCourseId());
        saved.setOnboardingCompleted(true);
        saved.setOnboardingCompletedAt(IndiaTime.now());
        return userRepository.save(saved);
    }

    private void validateFreemiumCourseSelection(FreemiumRegisterRequestDTO request, Optional<User> existing) {
        if (existing.isPresent()
                && existing.get().getChosenFreemiumCourseId() != null
                && !existing.get().getChosenFreemiumCourseId().isBlank()) {
            return;
        }
        List<FreemiumCourseOptionDTO> options = listSignupCourseOptions();
        if (options.isEmpty()) {
            return;
        }
        if (request.getFreemiumCourseId() == null || request.getFreemiumCourseId().isBlank()) {
            throw new IllegalArgumentException("Please choose a course to start with");
        }
        boolean valid = options.stream()
                .anyMatch(o -> o.getId().equals(request.getFreemiumCourseId().trim()));
        if (!valid) {
            throw new IllegalArgumentException("Please select a valid course");
        }
    }

    private void applyReferralOnSignup(User user, FreemiumRegisterRequestDTO request) {
        if (request.getReferralCode() == null || request.getReferralCode().isBlank()) {
            return;
        }
        if (user.getReferredBy() != null && !user.getReferredBy().isBlank()) {
            return;
        }
        userRepository.findByReferralCode(normalizeReferralCode(request.getReferralCode()))
                .ifPresent(referrer -> {
                    if (!referrer.getEmail().equalsIgnoreCase(user.getEmail())) {
                        user.setReferredBy(referrer.getReferralCode());
                        rewardReferrer(referrer);
                    }
                });
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
        String normEmail = userContactService.normalizeEmail(email);
        User user = userRepository.findByEmail(normEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(normEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        validateEligibleForFreemiumMigration(user);
        userContactService.assertContactUnique(normEmail, phone, user.getId());
        migrateUserToFreemiumPlan(user, phone);
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);
        return toStatusDto(user);
    }

    /**
     * OWNER maintenance: move all legacy users (planTier null) to FREEMIUM with product defaults.
     * Skips ADMIN/OWNER. Optionally includes inactive users and users without phone.
     *
     * @param dryRun when true, reports what would change without saving
     * @param includeInactive when true, migrates inactive learners and sets active=true
     * @param allowMissingPhone when true, migrates without requiring phone on file
     */
    @Transactional
    public LegacyFreemiumBackfillResultDTO backfillLegacyUsersToFreemium(
            boolean dryRun, boolean includeInactive, boolean allowMissingPhone) {
        List<User> legacy = userRepository.findByPlanTierIsNull();
        List<String> migratedEmails = new ArrayList<>();
        List<String> skippedNoPhoneEmails = new ArrayList<>();
        List<String> skippedStaffEmails = new ArrayList<>();
        int migrated = 0;
        int skippedStaff = 0;
        int skippedNoPhone = 0;
        int skippedInactive = 0;

        for (User user : legacy) {
            User.UserRole role = user.getEffectiveRole();
            if (role == User.UserRole.ADMIN || role == User.UserRole.OWNER) {
                skippedStaff++;
                if (user.getEmail() != null) {
                    skippedStaffEmails.add(user.getEmail());
                }
                continue;
            }
            if (!user.isActive() && !includeInactive) {
                skippedInactive++;
                continue;
            }
            boolean missingPhone = user.getPhone() == null || user.getPhone().isBlank();
            if (missingPhone && !allowMissingPhone) {
                skippedNoPhone++;
                if (user.getEmail() != null) {
                    skippedNoPhoneEmails.add(user.getEmail());
                }
                continue;
            }
            if (!dryRun) {
                migrateUserToFreemiumPlanForBackfill(user, allowMissingPhone);
                if (includeInactive && !user.isActive()) {
                    user.setActive(true);
                }
                user.setUpdatedAt(IndiaTime.now());
                userRepository.save(user);
            }
            migrated++;
            if (user.getEmail() != null) {
                migratedEmails.add(user.getEmail());
            }
        }

        return LegacyFreemiumBackfillResultDTO.builder()
                .dryRun(dryRun)
                .legacyUsersFound(legacy.size())
                .migrated(migrated)
                .skippedStaff(skippedStaff)
                .skippedNoPhone(skippedNoPhone)
                .skippedInactive(skippedInactive)
                .migratedEmails(migratedEmails)
                .skippedNoPhoneEmails(skippedNoPhoneEmails)
                .skippedStaffEmails(skippedStaffEmails)
                .build();
    }

    private void migrateUserToFreemiumPlan(User user, String phone) {
        validateEligibleForFreemiumMigration(user);
        applyFreemiumPlan(user, phone);
        initializeFreemiumDefaults(user);
    }

    private void migrateUserToFreemiumPlanForBackfill(User user, boolean allowMissingPhone) {
        validateEligibleForFreemiumMigration(user);
        if (allowMissingPhone) {
            applyFreemiumPlanWithoutPhoneRequirement(user);
        } else {
            applyFreemiumPlan(user, user.getPhone());
        }
        initializeFreemiumDefaults(user);
    }

    /** Admin backfill only — does not require phone when missing (collect on next login). */
    private void applyFreemiumPlanWithoutPhoneRequirement(User user) {
        user.setPlanTier(User.PlanTier.FREEMIUM);
        user.setEnabledModules(new ArrayList<>(FREEMIUM_BASE_MODULES));
        if (user.getReferralCode() == null) {
            user.setReferralCode(generateReferralCode());
        }
        if (user.getEmailVerified() == null) {
            user.setEmailVerified(true);
        }
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
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);
        return toStatusDto(user);
    }

    /**
     * Admin adjustment of a learner's AI wallet, in USD. Positive {@code deltaUsd} tops the
     * wallet base up, negative reduces it (never below zero). {@code newLimitUsd} sets an
     * absolute base instead. The permanent referral bonus is NOT touched — it stacks on top.
     */
    @Transactional
    public WalletAdjustResultDTO adjustWalletBalance(String userId, CreditAdjustRequestDTO request, String adminId) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        User user = requireUser(userId);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        if (aiUsageService.isUnlimitedForBudget(user)) {
            throw new IllegalStateException("User has an unlimited AI wallet (ENTERPRISE / legacy / staff)");
        }

        double baseBefore = aiUsageService.resolveWalletBaseUsd(user);
        double deltaUsd = request.getDeltaUsd() != null ? request.getDeltaUsd() : 0.0;
        double baseAfter = baseBefore;

        if (request.getNewLimitUsd() != null) {
            baseAfter = Math.max(0.0, request.getNewLimitUsd());
        } else if (deltaUsd != 0.0) {
            baseAfter = Math.max(0.0, baseBefore + deltaUsd);
        }

        user.setAiWalletLimitUsd(roundUsd(baseAfter));
        user.setUpdatedAt(IndiaTime.now());
        user.setUpdatedBy(adminId);
        userRepository.save(user);

        double bonus = user.getReferralBonusUsd() != null ? user.getReferralBonusUsd() : 0.0;
        return WalletAdjustResultDTO.builder()
                .userId(userId)
                .userEmail(user.getEmail())
                .adminId(adminId)
                .adminEmail(admin.getEmail())
                .deltaUsd(roundUsd(deltaUsd))
                .walletBeforeUsd(roundUsd(baseBefore))
                .walletAfterUsd(roundUsd(baseAfter))
                .referralBonusUsd(roundUsd(bonus))
                .effectiveLimitUsd(roundUsd(baseAfter + bonus))
                .reason(request.getReason().trim())
                .adjustedAt(IndiaTime.now())
                .build();
    }

    private static double roundUsd(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private void applyFreemiumPlan(User user, String phone) {
        if (phone != null && !phone.isBlank()) {
            user.setPhone(normalizePhone(phone));
        } else if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone is required to move to freemium plan");
        }
        user.setPlanTier(User.PlanTier.FREEMIUM);
        user.setSubscriptionPlanCode(SubscriptionService.PLAN_SPARK);
        user.setSubscriptionStatus(null);
        user.setCurrentPeriodEnd(null);
        user.setAiWalletLimitUsd(null);
        user.setEnabledModules(new ArrayList<>(FREEMIUM_BASE_MODULES));
        if (user.getReferralCode() == null) {
            user.setReferralCode(generateReferralCode());
        }
        if (user.getEmailVerified() == null) {
            user.setEmailVerified(true);
        }
    }

    private void applyPremiumPlan(User user, User.PlanTier tier) {
        user.setPlanTier(tier);
        user.setEnabledModules(new ArrayList<>(PREMIUM_MODULES));
    }

    public void initializeFreemiumDefaults(User user) {
        if (user.getPlanTier() == null) {
            user.setPlanTier(User.PlanTier.FREEMIUM);
        }
        if (user.getSubscriptionPlanCode() == null
                && (user.getPlanTier() == User.PlanTier.FREEMIUM
                        || user.getPlanTier() == null)) {
            user.setSubscriptionPlanCode(SubscriptionService.PLAN_SPARK);
        }
        if (user.getEnabledModules() == null || user.getEnabledModules().isEmpty()) {
            user.setEnabledModules(new ArrayList<>(FREEMIUM_BASE_MODULES));
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

    /** True when the learner's AI wallet is not metered at all (staff / ENTERPRISE / legacy). */
    public boolean hasUnlimitedWallet(User user) {
        return aiUsageService.isUnlimitedForBudget(user);
    }

    private FreemiumStatusDTO toStatusDto(User user) {
        return FreemiumStatusDTO.builder()
                .planTier(user.getPlanTier())
                .subscriptionPlanCode(user.getSubscriptionPlanCode())
                .subscriptionStatus(user.getSubscriptionStatus())
                .phone(user.getPhone())
                .emailVerified(user.getEmailVerified())
                .referralCode(user.getReferralCode())
                .referredBy(user.getReferredBy())
                .referralBonusUsd(user.getReferralBonusUsd())
                .credits(user.getCredits())
                .unlimitedQueries(aiUsageService.isUnlimitedForBudget(user))
                .enabledModules(user.getEnabledModules())
                .aiBudget(aiUsageService.getAiBudget(user))
                .build();
    }

    public AiBudgetDTO getAiBudgetForUser(User user) {
        return aiUsageService.getAiBudget(user);
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

    /** Validates freemium course selection during post-login onboarding. */
    public void applyFreemiumCourseOnOnboarding(User user, String freemiumCourseId) {
        if (user.getChosenFreemiumCourseId() != null && !user.getChosenFreemiumCourseId().isBlank()) {
            return;
        }
        FreemiumRegisterRequestDTO request = new FreemiumRegisterRequestDTO();
        request.setFreemiumCourseId(freemiumCourseId);
        validateFreemiumCourseSelection(request, Optional.of(user));
    }

    /** Applies referral code during onboarding (same rules as signup). */
    public void applyReferralOnOnboarding(User user, String referralCode) {
        if (referralCode == null || referralCode.isBlank()) {
            return;
        }
        FreemiumRegisterRequestDTO request = new FreemiumRegisterRequestDTO();
        request.setReferralCode(referralCode);
        applyReferralOnSignup(user, request);
    }

    /** Enrolls user in chosen freemium course after onboarding save. */
    public void enrollFreemiumCourseIfNeeded(User user, String freemiumCourseId) {
        if (user.getPlanTier() == User.PlanTier.FREEMIUM) {
            userCourseAccessService.applyUserChosenFreemiumCourse(user, freemiumCourseId);
        }
    }

}
