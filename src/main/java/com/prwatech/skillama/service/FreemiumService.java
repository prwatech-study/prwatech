package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.AiBudgetDTO;
import com.prwatech.skillama.dto.ConsumeQueryRequestDTO;
import com.prwatech.skillama.dto.CreditAdjustRequestDTO;
import com.prwatech.skillama.dto.CreditAdjustmentLogDTO;
import com.prwatech.skillama.dto.FreemiumCourseOptionDTO;
import com.prwatech.skillama.dto.FreemiumOfferingDTO;
import com.prwatech.skillama.dto.FreemiumRegisterRequestDTO;
import com.prwatech.skillama.dto.FreemiumStatusDTO;
import com.prwatech.skillama.dto.LegacyFreemiumBackfillResultDTO;
import com.prwatech.skillama.dto.QueryCreditRepairResultDTO;
import com.prwatech.skillama.dto.QueryCreditsDTO;
import com.prwatech.skillama.model.CreditAdjustmentLog;
import com.prwatech.skillama.repository.CreditAdjustmentLogRepository;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.QueryCreditLimitException;
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

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FreemiumService {

    /** Aligned with skillama-lms src/config/freemium.js and BACKEND_REQUIREMENTS_FREEMIUM.md */
    public static final int FREEMIUM_QUERY_LIMIT = 50;
    public static final int REFERRAL_QUERY_BONUS = 20;
    public static final String REFERRAL_BONUS_MODULE = "Debug";
    /** All freemium users get these modules from signup (referral adds query credits only). */
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
    private final CreditAdjustmentLogRepository creditAdjustmentLogRepository;
    private final UserContactService userContactService;
    private final AiUsageService aiUsageService;

    /** Public read — home page banner, signup copy (no auth). */
    public FreemiumOfferingDTO getPublicOffering() {
        return FreemiumOfferingDTO.builder()
                .baseModules(new ArrayList<>(FREEMIUM_BASE_MODULES))
                .modulesWithReferral(new ArrayList<>(FREEMIUM_REFERRAL_MODULES))
                .referralBonusModule(REFERRAL_BONUS_MODULE)
                .queryLimit(FREEMIUM_QUERY_LIMIT)
                .referralQueryBonus(REFERRAL_QUERY_BONUS)
                .queryLimitWithReferral(FREEMIUM_QUERY_LIMIT + REFERRAL_QUERY_BONUS)
                .courseSelectionAtSignup(true)
                .build();
    }

    public List<FreemiumCourseOptionDTO> listSignupCourseOptions() {
        return courseRepository.findAll().stream()
                // Only courses available to learners: not archived AND not admin-deactivated.
                .filter(c -> c.getDeletedAt() == null && !Boolean.FALSE.equals(c.getActive()))
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
        // Wallet metering applies to paid subscriptions even when query credits are unlimited
        aiUsageService.assertWithinBudget(user);
        if (!isUnlimited(user)) {
            int limit = effectiveLimit(user);
            int used = user.getQueryCreditsUsed() != null ? user.getQueryCreditsUsed() : 0;
            if (used >= limit) {
                throw new QueryCreditLimitException("Query credit limit reached", used, limit);
            }
            user.setQueryCreditsUsed(used + 1);
            user.setUpdatedAt(IndiaTime.now());
            user = userRepository.save(user);
        }

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
        applyReferralBenefits(user);
        user.setUpdatedAt(IndiaTime.now());
        userRepository.save(user);
        return toStatusDto(user);
    }

    public String getReferralCode(String userId) {
        User user = requireUser(userId);
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
        user.setQueryCreditsUsed(0);
        user.setQueryCreditsLimit(FREEMIUM_QUERY_LIMIT);
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
                        applyReferralBenefits(user);
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
        if (user.getQueryCreditsUsed() == null) {
            user.setQueryCreditsUsed(0);
        }
        if (hasReferralBonus(user)) {
            applyReferralBenefits(user);
        } else {
            user.setQueryCreditsLimit(FREEMIUM_QUERY_LIMIT);
            user.setEnabledModules(new ArrayList<>(FREEMIUM_BASE_MODULES));
        }
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
     * Admin adjustment: positive delta increases total allowance (limit) while preserving
     * lifetime used count — e.g. 29/50 + 100 → 29/150. Negative delta reduces allowance
     * (not below used). Optional newLimit sets absolute cap (not below used).
     */
    @Transactional
    public CreditAdjustmentLogDTO adjustQueryCredits(String userId, CreditAdjustRequestDTO request, String adminId) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        User user = requireUser(userId);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        if (isUnlimited(user)) {
            throw new IllegalStateException("User has unlimited credits (PAID/legacy)");
        }

        int usedBefore = user.getQueryCreditsUsed() != null ? user.getQueryCreditsUsed() : 0;
        int limitBefore = effectiveLimit(user);
        int delta = request.getDelta() != null ? request.getDelta() : 0;

        if (request.getNewLimit() != null) {
            user.setQueryCreditsLimit(Math.max(usedBefore, Math.max(0, request.getNewLimit())));
        } else if (delta != 0) {
            int newLimit = Math.max(usedBefore, limitBefore + delta);
            user.setQueryCreditsLimit(newLimit);
        }

        user.setUpdatedAt(IndiaTime.now());
        user.setUpdatedBy(adminId);
        userRepository.save(user);

        int usedAfter = user.getQueryCreditsUsed() != null ? user.getQueryCreditsUsed() : 0;
        CreditAdjustmentLog log = CreditAdjustmentLog.builder()
                .userId(userId)
                .userEmail(user.getEmail())
                .adminId(adminId)
                .adminEmail(admin.getEmail())
                .delta(delta)
                .balanceBeforeUsed(usedBefore)
                .balanceAfterUsed(usedAfter)
                .limitBefore(limitBefore)
                .limitAfter(user.getQueryCreditsLimit())
                .reason(request.getReason().trim())
                .createdAt(IndiaTime.now())
                .build();
        log = creditAdjustmentLogRepository.save(log);

        return CreditAdjustmentLogDTO.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .userEmail(log.getUserEmail())
                .adminId(log.getAdminId())
                .adminEmail(log.getAdminEmail())
                .delta(log.getDelta())
                .balanceBeforeUsed(log.getBalanceBeforeUsed())
                .balanceAfterUsed(log.getBalanceAfterUsed())
                .limitBefore(log.getLimitBefore())
                .limitAfter(log.getLimitAfter())
                .reason(log.getReason())
                .createdAt(log.getCreatedAt())
                .build();
    }

    /**
     * One-click repair for freemium query credits across all users.
     * <ul>
     *   <li><b>Used</b> — max of query activity log count, current stored used, and pre-corruption
     *       values from legacy admin adjustments that incorrectly reduced used on positive delta.</li>
     *   <li><b>Limit</b> — replays credit adjustment logs: applies limitAfter when set, adds positive
     *       deltas when the old bug left limit unchanged, then ensures limit &ge; used.</li>
     * </ul>
     */
    @Transactional
    public QueryCreditRepairResultDTO repairQueryCreditsForAllUsers(boolean dryRun) {
        QueryCreditRepairResultDTO result = QueryCreditRepairResultDTO.builder()
                .dryRun(dryRun)
                .build();

        for (User user : userRepository.findAll()) {
            result.setUsersScanned(result.getUsersScanned() + 1);
            if (isUnlimited(user)) {
                result.setUsersSkippedUnlimited(result.getUsersSkippedUnlimited() + 1);
                continue;
            }

            int usedBefore = user.getQueryCreditsUsed() != null ? user.getQueryCreditsUsed() : 0;
            int limitBefore = effectiveLimit(user);

            long activityCount = queryActivityLogRepository.countByUserId(user.getId());
            List<CreditAdjustmentLog> logs =
                    creditAdjustmentLogRepository.findByUserIdOrderByCreatedAtAsc(user.getId());

            int maxFromLegacyBug = logs.stream()
                    .filter(this::isLegacyPositiveDeltaUsedReduction)
                    .mapToInt(CreditAdjustmentLog::getBalanceBeforeUsed)
                    .max()
                    .orElse(0);

            int correctUsed = (int) Math.min(
                    Integer.MAX_VALUE,
                    Math.max(Math.max(activityCount, usedBefore), maxFromLegacyBug));

            int correctLimit = computeRepairedLimit(user, logs, correctUsed);

            if (usedBefore == correctUsed && limitBefore == correctLimit) {
                result.setUsersAlreadyCorrect(result.getUsersAlreadyCorrect() + 1);
                continue;
            }

            String note = buildRepairNote(usedBefore, correctUsed, limitBefore, correctLimit, activityCount, logs);

            result.getRepairs().add(QueryCreditRepairResultDTO.UserRepairDetail.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .usedBefore(usedBefore)
                    .usedAfter(correctUsed)
                    .limitBefore(limitBefore)
                    .limitAfter(correctLimit)
                    .activityLogCount(activityCount)
                    .note(note)
                    .build());

            if (!dryRun) {
                user.setQueryCreditsUsed(correctUsed);
                user.setQueryCreditsLimit(correctLimit);
                user.setUpdatedAt(IndiaTime.now());
                userRepository.save(user);
            }
            result.setUsersRepaired(result.getUsersRepaired() + 1);
        }

        return result;
    }

    private boolean isLegacyPositiveDeltaUsedReduction(CreditAdjustmentLog log) {
        return log.getDelta() > 0 && log.getBalanceAfterUsed() < log.getBalanceBeforeUsed();
    }

    private int computeRepairedLimit(User user, List<CreditAdjustmentLog> logs, int correctUsed) {
        int limit = hasReferralBonus(user)
                ? FREEMIUM_QUERY_LIMIT + REFERRAL_QUERY_BONUS
                : FREEMIUM_QUERY_LIMIT;

        for (CreditAdjustmentLog log : logs) {
            Integer before = log.getLimitBefore();
            Integer after = log.getLimitAfter();
            if (after != null && (before == null || !after.equals(before))) {
                limit = after;
            } else if (log.getDelta() > 0 && isLegacyPositiveDeltaUsedReduction(log)) {
                limit += log.getDelta();
            }
        }

        if (user.getQueryCreditsLimit() != null) {
            limit = Math.max(limit, user.getQueryCreditsLimit());
        }
        return Math.max(limit, correctUsed);
    }

    private String buildRepairNote(
            int usedBefore,
            int correctUsed,
            int limitBefore,
            int correctLimit,
            long activityCount,
            List<CreditAdjustmentLog> logs) {
        boolean legacyBug = logs.stream().anyMatch(this::isLegacyPositiveDeltaUsedReduction);
        if (legacyBug && usedBefore < correctUsed) {
            return "Restored used from activity/audit after legacy credit-adjust bug";
        }
        if (limitBefore < correctLimit) {
            return "Applied missing allowance from credit adjustment history";
        }
        if (activityCount > usedBefore) {
            return "Synced used count to query activity log";
        }
        return "Aligned used/limit with activity and adjustment history";
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
        if (user.getQueryCreditsUsed() == null) {
            user.setQueryCreditsUsed(0);
        }
        if (hasReferralBonus(user)) {
            applyReferralBenefits(user);
        } else {
            user.setQueryCreditsLimit(FREEMIUM_QUERY_LIMIT);
            user.setEnabledModules(new ArrayList<>(FREEMIUM_BASE_MODULES));
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
        if (user.getSubscriptionPlanCode() == null
                && (user.getPlanTier() == User.PlanTier.FREEMIUM
                        || user.getPlanTier() == null)) {
            user.setSubscriptionPlanCode(SubscriptionService.PLAN_SPARK);
        }
        if (user.getQueryCreditsLimit() == null && user.getPlanTier() == User.PlanTier.FREEMIUM) {
            user.setQueryCreditsLimit(FREEMIUM_QUERY_LIMIT);
        }
        if (user.getQueryCreditsUsed() == null) {
            user.setQueryCreditsUsed(0);
        }
        if (user.getEnabledModules() == null || user.getEnabledModules().isEmpty()) {
            user.setEnabledModules(new ArrayList<>(
                    hasReferralBonus(user) ? FREEMIUM_REFERRAL_MODULES : FREEMIUM_BASE_MODULES));
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

    /**
     * OWNER maintenance: align FREEMIUM users to product defaults (50 queries, 70 with referral, all 3 modules).
     */
    @Transactional
    public int normalizeFreemiumCreditLimits() {
        int updated = 0;
        for (User user : userRepository.findAll()) {
            if (user.getPlanTier() != User.PlanTier.FREEMIUM) {
                continue;
            }
            boolean changed = false;
            int target = hasReferralBonus(user)
                    ? FREEMIUM_QUERY_LIMIT + REFERRAL_QUERY_BONUS
                    : FREEMIUM_QUERY_LIMIT;
            Integer limit = user.getQueryCreditsLimit();
            if (limit == null || limit != target) {
                user.setQueryCreditsLimit(target);
                int used = user.getQueryCreditsUsed() != null ? user.getQueryCreditsUsed() : 0;
                if (used > target) {
                    user.setQueryCreditsUsed(target);
                }
                changed = true;
            }
            List<String> expectedModules = new ArrayList<>(FREEMIUM_BASE_MODULES);
            if (user.getEnabledModules() == null
                    || !new HashSet<>(expectedModules).equals(new HashSet<>(user.getEnabledModules()))) {
                user.setEnabledModules(expectedModules);
                changed = true;
            }
            if (changed) {
                user.setUpdatedAt(IndiaTime.now());
                userRepository.save(user);
                updated++;
            }
        }
        return updated;
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
        boolean unlimited = isUnlimited(user);
        return FreemiumStatusDTO.builder()
                .planTier(user.getPlanTier())
                .subscriptionPlanCode(user.getSubscriptionPlanCode())
                .subscriptionStatus(user.getSubscriptionStatus())
                .phone(user.getPhone())
                .emailVerified(user.getEmailVerified())
                .referralCode(user.getReferralCode())
                .referredBy(user.getReferredBy())
                .queryCreditsUsed(user.getQueryCreditsUsed())
                .queryCreditsLimit(unlimited ? null : effectiveLimit(user))
                .unlimitedQueries(unlimited)
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
