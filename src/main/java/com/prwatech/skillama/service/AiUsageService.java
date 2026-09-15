package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.AiBudgetDTO;
import com.prwatech.skillama.dto.AiUsageModuleBreakdownDTO;
import com.prwatech.skillama.dto.AiUsagePlatformSummaryDTO;
import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.dto.AiUsageSettingsDTO;
import com.prwatech.skillama.dto.AiUsageUserDetailDTO;
import com.prwatech.skillama.dto.AiUsageUserRowDTO;
import com.prwatech.skillama.dto.UpdateAiUsageSettingsDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.dto.EfficiencyAssumptionsDTO;
import com.prwatech.skillama.dto.EfficiencyEstimateDTO;
import com.prwatech.skillama.dto.UpdateEfficiencyAssumptionsDTO;
import com.prwatech.skillama.dto.WalletUsageBackfillResultDTO;
import com.prwatech.skillama.model.AiUsageEvent;
import com.prwatech.skillama.model.PlatformAiSettings;
import com.prwatech.skillama.model.PlatformEfficiencyAssumptions;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AiUsageEventRepository;
import com.prwatech.skillama.repository.PlatformAiSettingsRepository;
import com.prwatech.skillama.repository.PlatformEfficiencyAssumptionsRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiUsageService {

    private static final double DEFAULT_PLATFORM_BUDGET_USD = 1000.0;
    private static final double DEFAULT_FREEMIUM_BUDGET_USD = 0.30;
    private static final double DEFAULT_REFERRAL_REWARD_USD = 0.20;
    private static final double DEFAULT_COURSE_SHARE_REWARD_USD = 0.20;
    private static final double DEFAULT_CONSUMPTION_MULTIPLIER = 3.0;
    /** Display-only credits per USD of billed wallet (matches LMS creditsFormat.js). */
    private static final double USD_TO_CREDITS_RATE = 100.0;
    private static final double DEFAULT_TRANSCRIBE_PER_MINUTE_USD = 0.024;
    private static final double DEFAULT_TRANSCRIBE_MINIMUM_SECONDS = 15.0;
    private static final double DEFAULT_POLLY_PER_MILLION_CHARS_USD = 4.0;

    /**
     * Module + submodule a raw {@code endpoint} belongs to, for the learner-facing
     * "which module used my credits" breakdown. Includes Flask/backup aliases
     * ({@code handle_query}, {@code generate_lecture}, {@code audio_to_text}, …)
     * so those do not silently land in Other. Anything still unlisted is Other,
     * with a humanized endpoint name as the submodule label.
     */
    private static final Map<String, EndpointCategory> ENDPOINT_MODULE_MAP = Map.ofEntries(
            Map.entry("debug_assist", cat("Debug", "Debug assistant")),
            Map.entry("debug_code", cat("Debug", "Debug assistant")),
            Map.entry("code_execution_assist", cat("Code Execution", "Run code")),
            Map.entry("generate_output", cat("Code Execution", "Run code")),
            Map.entry("generate_practical_code", cat("Code Execution", "Practice code")),
            Map.entry("practice_code_generation", cat("Code Execution", "Practice code")),
            Map.entry("generate_code", cat("Code Execution", "Practice code")),
            Map.entry("chat_ask", cat("Ai-Tutor", "Chat questions")),
            Map.entry("handle_query", cat("Ai-Tutor", "Chat questions")),
            Map.entry("audio_to_text", cat("Ai-Tutor", "Voice to text")),
            Map.entry("text_to_speech", cat("Ai-Tutor", "Text to speech")),
            Map.entry("text_to_audio", cat("Ai-Tutor", "Text to speech")),
            Map.entry("introduce_tutor", cat("Ai-Tutor", "Text to speech")),
            Map.entry("lecture_start_instruction", cat("Ai-Tutor", "Text to speech")),
            Map.entry("ai_mentor_ask", cat("Ai-Tutor", "AI Mentor")),
            Map.entry("ai_mentor_follow_up", cat("Ai-Tutor", "AI Mentor")),
            Map.entry("generate_module_quiz", cat("Ai-Tutor", "Module quizzes")),
            Map.entry("generate_exam", cat("Ai-Tutor", "Exams")),
            Map.entry("ai_exam_recommendation", cat("Ai-Tutor", "Exams")),
            Map.entry("ai_exam_feedback", cat("Ai-Tutor", "Exams")),
            Map.entry("lecture_generation", cat("Lecture Generation", "Lecture audio and script")),
            Map.entry("generate_lecture", cat("Lecture Generation", "Lecture audio and script")),
            Map.entry("generate_image", cat("Course images", "Lesson diagrams")),
            Map.entry("generate_thumbnail", cat("Course images", "Course thumbnails")),
            Map.entry("generate_course_detail", cat("Course pages", "Detail page copy"))
    );

    private record EndpointCategory(String module, String submodule) {}

    /**
     * Wallet amount for an event. New events store billedUsd; legacy rows fall back
     * to rate-card costUsd (1×).
     */
    static double eventBilledUsd(AiUsageEvent event) {
        if (event == null) {
            return 0.0;
        }
        return event.getBilledUsd() != null ? event.getBilledUsd() : event.getCostUsd();
    }

    private static EndpointCategory cat(String module, String submodule) {
        return new EndpointCategory(module, submodule);
    }

    private final AiUsageEventRepository aiUsageEventRepository;
    private final PlatformAiSettingsRepository platformAiSettingsRepository;
    private final PlatformEfficiencyAssumptionsRepository platformEfficiencyAssumptionsRepository;
    private final SkillamaUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final UsdInrExchangeRateService usdInrExchangeRateService;
    private final TimeWalletService timeWalletService;

    @Value("${skillama.ai-usage.internal-api-key:}")
    private String internalApiKey;

    /** Shared daily AI-spend cap for the public demo course (USD). */
    @Value("${skillama.ai-usage.demo-daily-budget-usd:1.00}")
    private double demoDailyBudgetUsd;

    private JsonNode rateCard;

    @PostConstruct
    void loadRateCard() {
        try (InputStream in = new ClassPathResource("ai-usage-rate-card.json").getInputStream()) {
            rateCard = objectMapper.readTree(in);
        } catch (Exception e) {
            rateCard = objectMapper.createObjectNode();
        }
    }

    public boolean isValidInternalApiKey(String providedKey) {
        return internalApiKey != null
                && !internalApiKey.isBlank()
                && internalApiKey.equals(providedKey);
    }

    public PlatformAiSettings loadSettings() {
        PlatformAiSettings settings = platformAiSettingsRepository.findById(PlatformAiSettings.SINGLETON_ID)
                .orElseGet(this::defaultSettings);
        return normalizeSettings(settings);
    }

    private PlatformAiSettings normalizeSettings(PlatformAiSettings settings) {
        if (settings.getPlatformMonthlyBudgetUsd() <= 0) {
            settings.setPlatformMonthlyBudgetUsd(DEFAULT_PLATFORM_BUDGET_USD);
        }
        if (settings.getFreemiumMonthlyBudgetUsdPerUser() <= 0) {
            settings.setFreemiumMonthlyBudgetUsdPerUser(DEFAULT_FREEMIUM_BUDGET_USD);
        }
        if (settings.getReferralRewardUsd() <= 0) {
            settings.setReferralRewardUsd(DEFAULT_REFERRAL_REWARD_USD);
        }
        if (settings.getCourseShareRewardUsd() <= 0) {
            settings.setCourseShareRewardUsd(DEFAULT_COURSE_SHARE_REWARD_USD);
        }
        if (settings.getConsumptionMultiplier() < 1.0) {
            settings.setConsumptionMultiplier(DEFAULT_CONSUMPTION_MULTIPLIER);
        }
        return settings;
    }

    private PlatformAiSettings defaultSettings() {
        PlatformAiSettings settings = new PlatformAiSettings();
        settings.setId(PlatformAiSettings.SINGLETON_ID);
        settings.setDevModeEnabled(false);
        settings.setAiUsageTrackingEnabled(true);
        settings.setPlatformMonthlyBudgetUsd(DEFAULT_PLATFORM_BUDGET_USD);
        settings.setFreemiumMonthlyBudgetUsdPerUser(DEFAULT_FREEMIUM_BUDGET_USD);
        settings.setReferralRewardUsd(DEFAULT_REFERRAL_REWARD_USD);
        settings.setCourseShareRewardUsd(DEFAULT_COURSE_SHARE_REWARD_USD);
        settings.setConsumptionMultiplier(DEFAULT_CONSUMPTION_MULTIPLIER);
        return settings;
    }

    private double liveUsdToInrRate() {
        return usdInrExchangeRateService.getUsdToInrRate();
    }

    public AiUsageSettingsDTO getSettingsDto() {
        PlatformAiSettings settings = loadSettings();
        return toSettingsDto(settings);
    }

    public AiUsageSettingsDTO updateSettings(UpdateAiUsageSettingsDTO body, String ownerUserId) {
        if (body == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        PlatformAiSettings settings = loadSettings();
        settings.setId(PlatformAiSettings.SINGLETON_ID);
        if (body.getAiUsageTrackingEnabled() != null) {
            settings.setAiUsageTrackingEnabled(body.getAiUsageTrackingEnabled());
        }
        if (body.getPlatformMonthlyBudgetUsd() != null) {
            settings.setPlatformMonthlyBudgetUsd(Math.max(0, body.getPlatformMonthlyBudgetUsd()));
        }
        if (body.getFreemiumMonthlyBudgetUsdPerUser() != null) {
            settings.setFreemiumMonthlyBudgetUsdPerUser(Math.max(0, body.getFreemiumMonthlyBudgetUsdPerUser()));
        }
        if (body.getReferralRewardUsd() != null) {
            settings.setReferralRewardUsd(Math.max(0, body.getReferralRewardUsd()));
        }
        if (body.getCourseShareRewardUsd() != null) {
            settings.setCourseShareRewardUsd(Math.max(0, body.getCourseShareRewardUsd()));
        }
        if (body.getConsumptionMultiplier() != null
                && Double.isFinite(body.getConsumptionMultiplier())) {
            settings.setConsumptionMultiplier(Math.max(1.0, body.getConsumptionMultiplier()));
        }
        settings.setUpdatedAt(IndiaTime.now());
        settings.setUpdatedBy(ownerUserId);
        return toSettingsDto(platformAiSettingsRepository.save(settings));
    }

    private AiUsageSettingsDTO toSettingsDto(PlatformAiSettings settings) {
        return AiUsageSettingsDTO.builder()
                .aiUsageTrackingEnabled(settings.isAiUsageTrackingEnabled())
                .platformMonthlyBudgetUsd(settings.getPlatformMonthlyBudgetUsd())
                .freemiumMonthlyBudgetUsdPerUser(settings.getFreemiumMonthlyBudgetUsdPerUser())
                .referralRewardUsd(settings.getReferralRewardUsd())
                .courseShareRewardUsd(settings.getCourseShareRewardUsd())
                .consumptionMultiplier(settings.getConsumptionMultiplier())
                .usdToInrRate(liveUsdToInrRate())
                .usdToInrRateAsOf(usdInrExchangeRateService.getRateAsOfDate())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

    public PlatformEfficiencyAssumptions loadEfficiencyAssumptions() {
        return platformEfficiencyAssumptionsRepository.findById(PlatformEfficiencyAssumptions.SINGLETON_ID)
                .orElseGet(PlatformEfficiencyAssumptions::new);
    }

    public EfficiencyAssumptionsDTO getEfficiencyAssumptionsDto() {
        return toEfficiencyAssumptionsDto(loadEfficiencyAssumptions());
    }

    public EfficiencyAssumptionsDTO updateEfficiencyAssumptions(UpdateEfficiencyAssumptionsDTO body, String ownerUserId) {
        if (body == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        PlatformEfficiencyAssumptions assumptions = loadEfficiencyAssumptions();
        assumptions.setId(PlatformEfficiencyAssumptions.SINGLETON_ID);
        if (body.getAssumedManualQuizCreationMinutes() != null) {
            assumptions.setAssumedManualQuizCreationMinutes(Math.max(0, body.getAssumedManualQuizCreationMinutes()));
        }
        if (body.getAssumedManualExamCreationMinutes() != null) {
            assumptions.setAssumedManualExamCreationMinutes(Math.max(0, body.getAssumedManualExamCreationMinutes()));
        }
        if (body.getAssumedManualDoubtResolutionMinutes() != null) {
            assumptions.setAssumedManualDoubtResolutionMinutes(Math.max(0, body.getAssumedManualDoubtResolutionMinutes()));
        }
        if (body.getAssumedHourlyInstructorCostInr() != null) {
            assumptions.setAssumedHourlyInstructorCostInr(Math.max(0, body.getAssumedHourlyInstructorCostInr()));
        }
        assumptions.setUpdatedAt(IndiaTime.now());
        assumptions.setUpdatedBy(ownerUserId);
        return toEfficiencyAssumptionsDto(platformEfficiencyAssumptionsRepository.save(assumptions));
    }

    private EfficiencyAssumptionsDTO toEfficiencyAssumptionsDto(PlatformEfficiencyAssumptions assumptions) {
        return EfficiencyAssumptionsDTO.builder()
                .assumedManualQuizCreationMinutes(assumptions.getAssumedManualQuizCreationMinutes())
                .assumedManualExamCreationMinutes(assumptions.getAssumedManualExamCreationMinutes())
                .assumedManualDoubtResolutionMinutes(assumptions.getAssumedManualDoubtResolutionMinutes())
                .assumedHourlyInstructorCostInr(assumptions.getAssumedHourlyInstructorCostInr())
                .updatedAt(assumptions.getUpdatedAt())
                .build();
    }

    /**
     * Estimated time/cost saved this period = AI-handled volume (from {@link #getPlatformSummary})
     * × the admin-configured assumed manual baseline. Always an estimate — see
     * {@link PlatformEfficiencyAssumptions} for why no measured baseline exists.
     */
    public EfficiencyEstimateDTO getEfficiencyEstimate(String period) {
        AiUsagePlatformSummaryDTO summary = getPlatformSummary(period);
        PlatformEfficiencyAssumptions assumptions = loadEfficiencyAssumptions();

        double minutesSaved = summary.getQuizzesGenerated() * assumptions.getAssumedManualQuizCreationMinutes()
                + summary.getExamsGenerated() * assumptions.getAssumedManualExamCreationMinutes()
                + summary.getDoubtsResolved() * assumptions.getAssumedManualDoubtResolutionMinutes();
        double hoursSaved = minutesSaved / 60.0;
        double costSavedInr = hoursSaved * assumptions.getAssumedHourlyInstructorCostInr();

        return EfficiencyEstimateDTO.builder()
                .period(summary.getPeriod())
                .quizzesGenerated(summary.getQuizzesGenerated())
                .examsGenerated(summary.getExamsGenerated())
                .doubtsResolved(summary.getDoubtsResolved())
                .estimatedMinutesSaved(round(minutesSaved))
                .estimatedHoursSaved(round(hoursSaved))
                .estimatedCostSavedInr(round(costSavedInr))
                .build();
    }

    @Transactional
    public AiUsageEvent recordUsage(AiUsageRecordRequestDTO request) {
        PlatformAiSettings settings = loadSettings();
        if (!settings.isAiUsageTrackingEnabled()) {
            return null;
        }
        if (request == null || request.getEndpoint() == null || request.getEndpoint().isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        if ((request.getUserId() == null || request.getUserId().isBlank())
                && (request.getSessionId() == null || request.getSessionId().isBlank())) {
            throw new IllegalArgumentException("userId or sessionId is required");
        }

        int inputTokens = Math.max(0, request.getInputTokens() != null ? request.getInputTokens() : 0);
        int outputTokens = Math.max(0, request.getOutputTokens() != null ? request.getOutputTokens() : 0);
        int totalTokens = request.getTotalTokens() != null && request.getTotalTokens() > 0
                ? request.getTotalTokens()
                : inputTokens + outputTokens;

        String modelId = request.getModelId() != null ? request.getModelId() : "default";
        double audioSeconds = request.getAudioSeconds() != null ? Math.max(0.0, request.getAudioSeconds()) : 0.0;
        int pollyCharacters = request.getPollyCharacters() != null ? Math.max(0, request.getPollyCharacters()) : 0;
        double costUsd = computeCostUsd(modelId, inputTokens, outputTokens, audioSeconds, pollyCharacters);
        double costInr = round(costUsd * liveUsdToInrRate());
        double multiplier = settings.getConsumptionMultiplier();
        double billedUsd = round(costUsd * multiplier);
        double billedInr = round(billedUsd * liveUsdToInrRate());

        AiUsageEvent event = AiUsageEvent.builder()
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .endpoint(request.getEndpoint())
                .modelId(modelId)
                .courseId(request.getCourseId())
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .audioSeconds(audioSeconds > 0 ? audioSeconds : null)
                .pollyCharacters(pollyCharacters > 0 ? pollyCharacters : null)
                .costUsd(round(costUsd))
                .costInr(costInr)
                .billedUsd(billedUsd)
                .billedInr(billedInr)
                .consumptionMultiplier(multiplier)
                .createdAt(IndiaTime.now())
                .build();
        aiUsageEventRepository.save(event);

        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            User user = userRepository.findById(request.getUserId()).orElse(null);
            if (user != null) {
                ensureUsageAnchor(user);
                double used = user.getAiCostUsdThisPeriod() != null ? user.getAiCostUsdThisPeriod() : 0.0;
                user.setAiCostUsdThisPeriod(round(used + billedUsd));
                user.setUpdatedAt(IndiaTime.now());
                userRepository.save(user);
            }
        }

        return event;
    }

    public void assertWithinBudget(User user) {
        if (user == null) {
            return;
        }
        // Time-based (B2B seat) users: access requires remaining TIME — OR, once time is
        // exhausted, remaining EXPLICIT credit wallet (aiWalletLimitUsd). The unlimited
        // shortcut and the freemium default budget must NOT resurrect a spent time seat,
        // so the time branch is checked before isUnlimitedForBudget and falls through to
        // the credit gating below only when an explicit wallet exists.
        if (timeWalletService.isTimeWalletActive(user)) {
            if (timeWalletService.hasRemainingTime(user)) {
                return;
            }
            if (!hasExplicitWallet(user)) {
                timeWalletService.assertWithinTimeBudget(user);
            }
        } else if (isUnlimitedForBudget(user)) {
            return;
        }
        PlatformAiSettings settings = loadSettings();
        if (!settings.isAiUsageTrackingEnabled()) {
            return;
        }
        ensureUsageAnchor(user);
        double limit = resolveBudgetLimitUsd(user, settings);
        double used = user.getAiCostUsdThisPeriod() != null ? user.getAiCostUsdThisPeriod() : 0.0;
        if (used >= limit) {
            throw new AiBudgetLimitException("AI budget limit reached for this billing period", used, limit);
        }
    }

    public boolean isWithinBudget(User user) {
        try {
            assertWithinBudget(user);
            return true;
        } catch (AiBudgetLimitException e) {
            return false;
        }
    }

    /** Explicit paid/admin-granted USD wallet — distinct from the freemium default budget. */
    private boolean hasExplicitWallet(User user) {
        return user.getAiWalletLimitUsd() != null && user.getAiWalletLimitUsd() > 0;
    }

    /**
     * Gate for non-AI learning activity (lecture progress). Credit-wallet users learn freely —
     * only time-based seats are gated here, with the same time-OR-explicit-credits fallback
     * as assertWithinBudget. Throws TimeBudgetLimitException when both are exhausted.
     */
    public void assertLearningAccess(String userId) {
        if (userId == null) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !timeWalletService.isTimeWalletActive(user)) {
            return;
        }
        if (timeWalletService.hasRemainingTime(user)) {
            return;
        }
        if (hasExplicitWallet(user)) {
            PlatformAiSettings settings = loadSettings();
            if (!settings.isAiUsageTrackingEnabled()) {
                return;
            }
            ensureUsageAnchor(user);
            double used = user.getAiCostUsdThisPeriod() != null ? user.getAiCostUsdThisPeriod() : 0.0;
            if (used < resolveBudgetLimitUsd(user, settings)) {
                return;
            }
        }
        timeWalletService.assertWithinTimeBudget(user);
    }

    /**
     * Shared DAILY AI-spend budget for the public demo course — one pool across ALL
     * anonymous visitors (India-time day). Used to cap demo cost (default $1/day).
     */
    public AiBudgetDTO getDemoDailyBudget(String courseId) {
        double rate = liveUsdToInrRate();
        LocalDate today = IndiaTime.now().toLocalDate();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);
        double usedUsd = 0.0;
        if (courseId != null) {
            usedUsd = aiUsageEventRepository
                    .findByCourseIdAndCreatedAtBetween(courseId, start, end)
                    .stream()
                    // Count only anonymous/guest (demo) spend — the same course may
                    // also be used by logged-in learners, who must not affect the cap.
                    .filter(e -> e.getUserId() == null || e.getUserId().isBlank())
                    .mapToDouble(AiUsageEvent::getCostUsd)
                    .sum();
        }
        double limitUsd = demoDailyBudgetUsd;
        double remainingUsd = Math.max(0, limitUsd - usedUsd);
        return AiBudgetDTO.builder()
                .usedUsd(round(usedUsd))
                .limitUsd(round(limitUsd))
                .remainingUsd(round(remainingUsd))
                .usedInr(round(usedUsd * rate))
                .limitInr(round(limitUsd * rate))
                .remainingInr(round(remainingUsd * rate))
                .unlimited(false)
                .limitReached(usedUsd >= limitUsd)
                .build();
    }

    public AiBudgetDTO getAiBudget(User user) {
        PlatformAiSettings settings = loadSettings();
        double rate = liveUsdToInrRate();
        if (user == null || isUnlimitedForBudget(user)) {
            return AiBudgetDTO.builder()
                    .unlimited(true)
                    .limitReached(false)
                    .referralBonusUsd(user != null ? round(referralBonusUsd(user)) : null)
                    .shareBonusUsd(user != null ? round(shareBonusUsd(user)) : null)
                    .build();
        }
        ensureUsageAnchor(user);
        double limitUsd = resolveBudgetLimitUsd(user, settings);
        double usedUsd = user.getAiCostUsdThisPeriod() != null ? user.getAiCostUsdThisPeriod() : 0.0;
        double remainingUsd = Math.max(0, limitUsd - usedUsd);
        return AiBudgetDTO.builder()
                .usedUsd(round(usedUsd))
                .limitUsd(round(limitUsd))
                .remainingUsd(round(remainingUsd))
                .usedInr(round(usedUsd * rate))
                .limitInr(round(limitUsd * rate))
                .remainingInr(round(remainingUsd * rate))
                .referralBonusUsd(round(referralBonusUsd(user)))
                .shareBonusUsd(round(shareBonusUsd(user)))
                .unlimited(false)
                .limitReached(usedUsd >= limitUsd)
                .build();
    }

    public AiUsagePlatformSummaryDTO getPlatformSummary(String period) {
        PlatformAiSettings settings = loadSettings();
        PeriodRange range = resolvePeriodRange(period);
        List<AiUsageEvent> events = aiUsageEventRepository.findByCreatedAtBetween(range.start(), range.end());

        long inputTokens = events.stream().mapToLong(AiUsageEvent::getInputTokens).sum();
        long outputTokens = events.stream().mapToLong(AiUsageEvent::getOutputTokens).sum();
        long totalTokens = events.stream().mapToLong(AiUsageEvent::getTotalTokens).sum();
        double totalCostUsd = round(events.stream().mapToDouble(AiUsageEvent::getCostUsd).sum());
        double totalCostInr = round(totalCostUsd * liveUsdToInrRate());
        double totalBilledUsd = round(events.stream().mapToDouble(AiUsageService::eventBilledUsd).sum());
        double totalBilledInr = round(totalBilledUsd * liveUsdToInrRate());

        Set<String> usersWithUsage = events.stream()
                .map(AiUsageEvent::getUserId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        long totalActiveUsers = userRepository.findAll().stream()
                .filter(u -> u.getEffectiveRole() == User.UserRole.USER && u.isActive())
                .count();

        int daysElapsed = Math.max(1, (int) ChronoUnit.DAYS.between(range.periodStartDate(), range.periodEndDate()) + 1);
        double avgPerUserUsd = usersWithUsage.isEmpty() ? 0 : totalCostUsd / usersWithUsage.size();
        double avgPerUserInr = avgPerUserUsd * liveUsdToInrRate();
        double avgPerUserPerDayUsd = avgPerUserUsd / daysElapsed;
        double avgPerUserPerDayInr = avgPerUserInr / daysElapsed;

        double budgetUsd = settings.getPlatformMonthlyBudgetUsd();
        double budgetRemaining = Math.max(0, budgetUsd - totalCostUsd);
        double utilization = budgetUsd > 0 ? (totalCostUsd / budgetUsd) * 100.0 : 0.0;
        double projectedMonthEnd = (totalCostUsd / daysElapsed) * range.daysInMonth();

        long quizzesGenerated = events.stream()
                .filter(e -> "generate_module_quiz".equals(e.getEndpoint()))
                .count();
        long examsGenerated = events.stream()
                .filter(e -> "generate_exam".equals(e.getEndpoint()))
                .count();
        long doubtsResolved = events.stream()
                .filter(e -> Set.of("chat_ask", "ai_mentor_ask", "ai_mentor_follow_up").contains(e.getEndpoint()))
                .count();

        return AiUsagePlatformSummaryDTO.builder()
                .period(normalizePeriod(period))
                .dateRange(AiUsagePlatformSummaryDTO.LocalDateRangeDTO.builder()
                        .start(range.periodStartDate().toString())
                        .end(range.periodEndDate().toString())
                        .build())
                .totalInputTokens(inputTokens)
                .totalOutputTokens(outputTokens)
                .totalTokens(totalTokens)
                .totalCostUsd(totalCostUsd)
                .totalCostInr(totalCostInr)
                .totalBilledUsd(totalBilledUsd)
                .totalBilledInr(totalBilledInr)
                .platformMonthlyBudgetUsd(budgetUsd)
                .budgetRemainingUsd(round(budgetRemaining))
                .budgetUtilizationPercent(round(utilization))
                .activeUsersWithUsage(usersWithUsage.size())
                .totalActiveUsers(totalActiveUsers)
                .avgCostPerUserUsd(round(avgPerUserUsd))
                .avgCostPerUserInr(round(avgPerUserInr))
                .avgCostPerUserPerDayUsd(round(avgPerUserPerDayUsd))
                .avgCostPerUserPerDayInr(round(avgPerUserPerDayInr))
                .daysElapsedInPeriod(daysElapsed)
                .projectedMonthEndCostUsd(round(projectedMonthEnd))
                .usdToInrRate(liveUsdToInrRate())
                .quizzesGenerated(quizzesGenerated)
                .examsGenerated(examsGenerated)
                .doubtsResolved(doubtsResolved)
                .build();
    }

    public List<AiUsageUserRowDTO> listUserUsage(String period) {
        PlatformAiSettings settings = loadSettings();
        PeriodRange range = resolvePeriodRange(period);
        List<AiUsageEvent> events = aiUsageEventRepository.findByCreatedAtBetween(range.start(), range.end());
        Map<String, List<AiUsageEvent>> byUser = events.stream()
                .filter(e -> e.getUserId() != null && !e.getUserId().isBlank())
                .collect(Collectors.groupingBy(AiUsageEvent::getUserId));

        List<AiUsageUserRowDTO> rows = new ArrayList<>();
        for (Map.Entry<String, List<AiUsageEvent>> entry : byUser.entrySet()) {
            User user = userRepository.findById(entry.getKey()).orElse(null);
            List<AiUsageEvent> userEvents = entry.getValue();
            long input = userEvents.stream().mapToLong(AiUsageEvent::getInputTokens).sum();
            long output = userEvents.stream().mapToLong(AiUsageEvent::getOutputTokens).sum();
            long total = userEvents.stream().mapToLong(AiUsageEvent::getTotalTokens).sum();
            double costUsd = round(userEvents.stream().mapToDouble(AiUsageEvent::getCostUsd).sum());
            double costInr = round(costUsd * liveUsdToInrRate());
            double billedUsd = round(userEvents.stream().mapToDouble(AiUsageService::eventBilledUsd).sum());
            double billedInr = round(billedUsd * liveUsdToInrRate());
            Double freemiumCap = null;
            Double usedPct = null;
            if (user != null && !isUnlimitedForBudget(user)) {
                freemiumCap = resolveBudgetLimitUsd(user, settings);
                usedPct = freemiumCap > 0 ? round((billedUsd / freemiumCap) * 100.0) : 0.0;
            }
            rows.add(AiUsageUserRowDTO.builder()
                    .userId(entry.getKey())
                    .name(user != null ? user.getName() : null)
                    .email(user != null ? user.getEmail() : null)
                    .inputTokens(input)
                    .outputTokens(output)
                    .totalTokens(total)
                    .costUsd(costUsd)
                    .costInr(costInr)
                    .billedUsd(billedUsd)
                    .billedInr(billedInr)
                    .freemiumBudgetUsd(freemiumCap)
                    .budgetUsedPercent(usedPct)
                    .build());
        }
        rows.sort(Comparator.comparing(AiUsageUserRowDTO::getBilledUsd).reversed());
        return rows;
    }

    public AiUsageUserDetailDTO getUserUsageDetail(String userId, String period) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        PlatformAiSettings settings = loadSettings();
        PeriodRange range = resolvePeriodRange(period);
        List<AiUsageEvent> events = aiUsageEventRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, range.start(), range.end());

        long input = events.stream().mapToLong(AiUsageEvent::getInputTokens).sum();
        long output = events.stream().mapToLong(AiUsageEvent::getOutputTokens).sum();
        long total = events.stream().mapToLong(AiUsageEvent::getTotalTokens).sum();
        double costUsd = round(events.stream().mapToDouble(AiUsageEvent::getCostUsd).sum());
        double costInr = round(costUsd * liveUsdToInrRate());
        double billedUsd = round(events.stream().mapToDouble(AiUsageService::eventBilledUsd).sum());
        double billedInr = round(billedUsd * liveUsdToInrRate());

        Map<String, AiUsageUserDetailDTO.EndpointBreakdownDTO> breakdownMap = new HashMap<>();
        for (AiUsageEvent event : events) {
            String endpoint = event.getEndpoint() != null ? event.getEndpoint() : "unknown";
            double eventBilled = eventBilledUsd(event);
            AiUsageUserDetailDTO.EndpointBreakdownDTO existing = breakdownMap.get(endpoint);
            if (existing == null) {
                breakdownMap.put(endpoint, AiUsageUserDetailDTO.EndpointBreakdownDTO.builder()
                        .endpoint(endpoint)
                        .inputTokens(event.getInputTokens())
                        .outputTokens(event.getOutputTokens())
                        .costUsd(event.getCostUsd())
                        .billedUsd(eventBilled)
                        .callCount(1)
                        .build());
            } else {
                existing.setInputTokens(existing.getInputTokens() + event.getInputTokens());
                existing.setOutputTokens(existing.getOutputTokens() + event.getOutputTokens());
                existing.setCostUsd(round(existing.getCostUsd() + event.getCostUsd()));
                existing.setBilledUsd(round(existing.getBilledUsd() + eventBilled));
                existing.setCallCount(existing.getCallCount() + 1);
            }
        }

        List<AiUsageUserDetailDTO.EndpointBreakdownDTO> breakdown = new ArrayList<>(breakdownMap.values());
        breakdown.sort(Comparator.comparing(AiUsageUserDetailDTO.EndpointBreakdownDTO::getBilledUsd).reversed());

        return AiUsageUserDetailDTO.builder()
                .userId(userId)
                .name(user.getName())
                .email(user.getEmail())
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(total)
                .costUsd(costUsd)
                .costInr(costInr)
                .billedUsd(billedUsd)
                .billedInr(billedInr)
                .aiBudget(getAiBudget(user))
                .byEndpoint(breakdown)
                .build();
    }

    /**
     * Learner-facing "which module used my AI credits" breakdown for the user's CURRENT
     * billing period (same period boundary as {@link #getAiBudget}, so the sum of
     * {@code byModule[].credits} matches the "used" figure shown in the credits badge).
     * Credits are billed (rate-card × multiplier), not raw AWS API cost.
     * Read-only, like getAiBudget: ensureUsageAnchor is applied in-memory to anchor the
     * right window but is not persisted (persistence happens on the next recordUsage call).
     */
    public AiUsageModuleBreakdownDTO getUserModuleBreakdown(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ensureUsageAnchor(user);

        LocalDateTime start = user.getAiCostPeriodStart() != null
                ? user.getAiCostPeriodStart()
                : IndiaTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = IndiaTime.now();

        List<AiUsageEvent> events = aiUsageEventRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, start, end);

        Map<String, Double> billedByModule = new HashMap<>();
        for (AiUsageEvent event : events) {
            EndpointCategory category = categorizeEndpoint(event.getEndpoint());
            billedByModule.merge(category.module(), eventBilledUsd(event), Double::sum);
        }

        List<AiUsageModuleBreakdownDTO.ModuleUsageDTO> breakdown = new ArrayList<>();
        for (Map.Entry<String, Double> entry : billedByModule.entrySet()) {
            double moduleBilled = round(entry.getValue());
            breakdown.add(AiUsageModuleBreakdownDTO.ModuleUsageDTO.builder()
                    .module(entry.getKey())
                    .credits(usdToCredits(moduleBilled))
                    .build());
        }
        breakdown.sort(Comparator.comparing(AiUsageModuleBreakdownDTO.ModuleUsageDTO::getCredits).reversed());

        double totalCredits = round(breakdown.stream()
                .mapToDouble(AiUsageModuleBreakdownDTO.ModuleUsageDTO::getCredits)
                .sum());
        for (AiUsageModuleBreakdownDTO.ModuleUsageDTO row : breakdown) {
            row.setPercentOfTotal(totalCredits > 0
                    ? round((row.getCredits() / totalCredits) * 100.0)
                    : 0.0);
        }

        return AiUsageModuleBreakdownDTO.builder()
                .periodStart(start)
                .totalCredits(totalCredits)
                .byModule(breakdown)
                .build();
    }

    private EndpointCategory categorizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return cat("Other", "Unknown");
        }
        String key = endpoint.startsWith("/") ? endpoint.substring(1) : endpoint.trim();
        EndpointCategory mapped = ENDPOINT_MODULE_MAP.get(key);
        if (mapped != null) {
            return mapped;
        }
        return cat("Other", humanizeEndpoint(key));
    }

    static String humanizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "Unknown";
        }
        String[] parts = endpoint.replace('-', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.length() == 0 ? "Unknown" : sb.toString();
    }

    /**
     * GROUND RULE (product): credits, once given, are lifetime — and consumption is
     * NEVER reset by any flow, for any plan tier. There is no periodic refresh of any
     * kind: subscription renewals TOP UP the wallet limit (SubscriptionService), earned
     * referral/share bonuses raise it, and the consumed counter only ever grows
     * (recordUsage) or is repaired from raw events (backfillWalletUsage). A user at
     * 48/50 used stays at 48/50 until new credits are granted. This method only
     * initializes the tracking fields on first use — it must never zero an existing
     * counter.
     */
    private void ensureUsageAnchor(User user) {
        if (user.getAiCostPeriodStart() == null) {
            user.setAiCostPeriodStart(IndiaTime.now());
        }
        if (user.getAiCostUsdThisPeriod() == null) {
            user.setAiCostUsdThisPeriod(0.0);
        }
    }

    /**
     * OWNER maintenance: repair aiCostUsdThisPeriod for wallet users whose counter was
     * wiped by the now-removed time-based resets (monthly / lapsed-subscription — see
     * ensureUsageAnchor: consumption is lifetime and never reset). Recomputes each
     * user's LIFETIME consumption from ai_usage_events, the per-call source of truth
     * the resets never touched, and re-anchors the period to the earliest event.
     * Users inside an ACTIVE subscription period are skipped — their counter was
     * legitimately reset by the renewal payment and is live data. Idempotent; dryRun
     * previews the per-user before/after without writing.
     */
    @Transactional
    public WalletUsageBackfillResultDTO backfillWalletUsage(boolean dryRun) {
        LocalDateTime now = IndiaTime.now();
        List<User> candidates = userRepository.findByAiWalletLimitUsdGreaterThan(0.0);
        List<WalletUsageBackfillResultDTO.EntryDTO> entries = new ArrayList<>();
        int skippedActivePeriod = 0;

        for (User user : candidates) {
            if (user.getCurrentPeriodEnd() != null && !now.isAfter(user.getCurrentPeriodEnd())) {
                skippedActivePeriod++;
                continue;
            }
            List<AiUsageEvent> events = aiUsageEventRepository.findByUserId(user.getId());
            double recomputed = round(events.stream()
                    .mapToDouble(AiUsageService::eventBilledUsd)
                    .sum());
            LocalDateTime anchor = events.stream()
                    .map(AiUsageEvent::getCreatedAt)
                    .filter(java.util.Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(now);
            double before = user.getAiCostUsdThisPeriod() != null ? user.getAiCostUsdThisPeriod() : 0.0;
            entries.add(WalletUsageBackfillResultDTO.EntryDTO.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .beforeUsd(before)
                    .afterUsd(recomputed)
                    .build());
            if (!dryRun) {
                user.setAiCostPeriodStart(anchor);
                user.setAiCostUsdThisPeriod(recomputed);
                user.setUpdatedAt(now);
                userRepository.save(user);
            }
        }

        return WalletUsageBackfillResultDTO.builder()
                .dryRun(dryRun)
                .candidatesScanned(candidates.size())
                .updated(entries.size())
                .skippedActivePeriod(skippedActivePeriod)
                .entries(entries)
                .build();
    }

    /**
     * Paid subscription wallets use aiWalletLimitUsd.
     * Spark / freemium uses platform freemiumMonthlyBudgetUsdPerUser.
     * ENTERPRISE, admin roles, legacy null tier, and legacy PAID without a wallet stay unlimited.
     */
    public boolean isUnlimitedForBudget(User user) {
        if (user.getEffectiveRole() == User.UserRole.ADMIN
                || user.getEffectiveRole() == User.UserRole.OWNER) {
            return true;
        }
        if (user.getPlanTier() == null) {
            return true;
        }
        if (user.getPlanTier() == User.PlanTier.ENTERPRISE) {
            return true;
        }
        // Legacy admin "mark paid" without a subscription wallet remains unlimited
        if (user.getPlanTier() == User.PlanTier.PAID
                && (user.getAiWalletLimitUsd() == null || user.getAiWalletLimitUsd() <= 0)) {
            return true;
        }
        return false;
    }

    private double resolveBudgetLimitUsd(User user, PlatformAiSettings settings) {
        double base = (user.getAiWalletLimitUsd() != null && user.getAiWalletLimitUsd() > 0)
                ? user.getAiWalletLimitUsd() : settings.getFreemiumMonthlyBudgetUsdPerUser();
        double bonus = referralBonusUsd(user) + shareBonusUsd(user);
        return base + bonus;
    }

    /**
     * Effective wallet BASE in USD, excluding the referral bonus — the paid-plan wallet when set,
     * otherwise the platform freemium per-user budget. Used by admin wallet adjustments.
     */
    public double resolveWalletBaseUsd(User user) {
        PlatformAiSettings settings = loadSettings();
        return (user.getAiWalletLimitUsd() != null && user.getAiWalletLimitUsd() > 0)
                ? user.getAiWalletLimitUsd() : settings.getFreemiumMonthlyBudgetUsdPerUser();
    }

    private double referralBonusUsd(User user) {
        return user.getReferralBonusUsd() != null ? user.getReferralBonusUsd() : 0.0;
    }

    private double shareBonusUsd(User user) {
        return user.getShareBonusUsd() != null ? user.getShareBonusUsd() : 0.0;
    }

    /**
     * Prices a single AI call for display (e.g. admin "this image cost ₹X"),
     * without persisting a usage event. Reuses the same rate card + live FX rate.
     */
    public com.prwatech.skillama.dto.AiCostEstimateDTO estimateCost(String modelId, int inputTokens, int outputTokens) {
        int in = Math.max(0, inputTokens);
        int out = Math.max(0, outputTokens);
        double usd = computeCostUsd(
                modelId != null && !modelId.isBlank() ? modelId : "default", in, out, 0.0, 0);
        double rate = liveUsdToInrRate();
        return com.prwatech.skillama.dto.AiCostEstimateDTO.builder()
                .costUsd(round(usd))
                .costInr(round(usd * rate))
                .usdToInrRate(rate)
                .inputTokens(in)
                .outputTokens(out)
                .totalTokens(in + out)
                .build();
    }

    private double computeCostUsd(String modelId, int inputTokens, int outputTokens) {
        return computeCostUsd(modelId, inputTokens, outputTokens, 0.0, 0);
    }

    private double computeCostUsd(
            String modelId, int inputTokens, int outputTokens, double audioSeconds, int pollyCharacters) {
        JsonNode modelRates = rateCard.path("models").path(modelId);
        if (modelRates.isMissingNode()) {
            modelRates = rateCard.path("default");
        }
        double inputRate = modelRates.path("inputPer1kTokensUsd").asDouble(0.0003);
        double outputRate = modelRates.path("outputPer1kTokensUsd").asDouble(0.0006);
        double tokenCost = (inputTokens / 1000.0) * inputRate + (outputTokens / 1000.0) * outputRate;

        JsonNode media = rateCard.path("media");
        double transcribePerMinute = media.path("transcribePerMinuteUsd")
                .asDouble(DEFAULT_TRANSCRIBE_PER_MINUTE_USD);
        double transcribeMinimumSeconds = media.path("transcribeMinimumSeconds")
                .asDouble(DEFAULT_TRANSCRIBE_MINIMUM_SECONDS);
        double pollyPerMillion = media.path("pollyStandardPerMillionCharsUsd")
                .asDouble(DEFAULT_POLLY_PER_MILLION_CHARS_USD);

        double transcribeCost = 0.0;
        if (audioSeconds > 0) {
            double billedSeconds = Math.max(audioSeconds, transcribeMinimumSeconds);
            transcribeCost = (billedSeconds / 60.0) * transcribePerMinute;
        }
        double pollyCost = pollyCharacters > 0
                ? (pollyCharacters / 1_000_000.0) * pollyPerMillion
                : 0.0;
        return tokenCost + transcribeCost + pollyCost;
    }

    /**
     * AWS Polly does not bill SSML tags. Count remaining characters after stripping tags
     * and unescaping a few common entities.
     */
    public static int billedPollyCharacters(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String stripped = text.replaceAll("(?s)<[^>]+>", "");
        stripped = stripped.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
        return stripped.length();
    }

    /** Best-effort sibling event so learners see Text to speech on the usage page. */
    public void recordSpeechUsage(User user, String courseId, String spokenText) {
        recordSpeechUsage(user, courseId, billedPollyCharacters(spokenText));
    }

    public void recordSpeechUsage(User user, String courseId, int pollyCharacters) {
        if (user == null || pollyCharacters <= 0) {
            return;
        }
        try {
            recordUsage(AiUsageRecordRequestDTO.builder()
                    .userId(user.getId())
                    .courseId(courseId)
                    .endpoint("text_to_speech")
                    .pollyCharacters(pollyCharacters)
                    .inputTokens(0)
                    .outputTokens(0)
                    .build());
        } catch (Exception ignored) {
            // Tracking must never fail a delivered lecture/reply.
        }
    }

    /** Best-effort sibling event so learners see Voice to text on the usage page. */
    public void recordTranscribeUsage(User user, String courseId, Double audioSeconds) {
        if (user == null || audioSeconds == null || audioSeconds <= 0) {
            return;
        }
        try {
            recordUsage(AiUsageRecordRequestDTO.builder()
                    .userId(user.getId())
                    .courseId(courseId)
                    .endpoint("audio_to_text")
                    .audioSeconds(audioSeconds)
                    .inputTokens(0)
                    .outputTokens(0)
                    .build());
        } catch (Exception ignored) {
            // Tracking must never fail a delivered transcription.
        }
    }

    private double usdToCredits(double usd) {
        return Math.round(usd * USD_TO_CREDITS_RATE * 10.0) / 10.0;
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private String normalizePeriod(String period) {
        return period == null || period.isBlank() ? "month" : period.trim().toLowerCase();
    }

    private PeriodRange resolvePeriodRange(String period) {
        LocalDate today = IndiaTime.now().toLocalDate();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today;
        LocalDateTime start = monthStart.atStartOfDay();
        LocalDateTime end = monthEnd.plusDays(1).atStartOfDay().minusNanos(1);
        return new PeriodRange(start, end, monthStart, monthEnd, monthStart.lengthOfMonth());
    }

    private record PeriodRange(
            LocalDateTime start,
            LocalDateTime end,
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            int daysInMonth) {}
}
