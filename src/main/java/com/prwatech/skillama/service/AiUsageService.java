package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.AiBudgetDTO;
import com.prwatech.skillama.dto.AiUsagePlatformSummaryDTO;
import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.dto.AiUsageSettingsDTO;
import com.prwatech.skillama.dto.AiUsageUserDetailDTO;
import com.prwatech.skillama.dto.AiUsageUserRowDTO;
import com.prwatech.skillama.dto.UpdateAiUsageSettingsDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AiUsageEvent;
import com.prwatech.skillama.model.PlatformAiSettings;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AiUsageEventRepository;
import com.prwatech.skillama.repository.PlatformAiSettingsRepository;
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
    private static final double DEFAULT_FREEMIUM_BUDGET_USD = 0.50;

    private final AiUsageEventRepository aiUsageEventRepository;
    private final PlatformAiSettingsRepository platformAiSettingsRepository;
    private final SkillamaUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final UsdInrExchangeRateService usdInrExchangeRateService;

    @Value("${skillama.ai-usage.internal-api-key:}")
    private String internalApiKey;

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
        return settings;
    }

    private PlatformAiSettings defaultSettings() {
        PlatformAiSettings settings = new PlatformAiSettings();
        settings.setId(PlatformAiSettings.SINGLETON_ID);
        settings.setDevModeEnabled(false);
        settings.setAiUsageTrackingEnabled(true);
        settings.setPlatformMonthlyBudgetUsd(DEFAULT_PLATFORM_BUDGET_USD);
        settings.setFreemiumMonthlyBudgetUsdPerUser(DEFAULT_FREEMIUM_BUDGET_USD);
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
        settings.setUpdatedAt(IndiaTime.now());
        settings.setUpdatedBy(ownerUserId);
        return toSettingsDto(platformAiSettingsRepository.save(settings));
    }

    private AiUsageSettingsDTO toSettingsDto(PlatformAiSettings settings) {
        return AiUsageSettingsDTO.builder()
                .aiUsageTrackingEnabled(settings.isAiUsageTrackingEnabled())
                .platformMonthlyBudgetUsd(settings.getPlatformMonthlyBudgetUsd())
                .freemiumMonthlyBudgetUsdPerUser(settings.getFreemiumMonthlyBudgetUsdPerUser())
                .usdToInrRate(liveUsdToInrRate())
                .usdToInrRateAsOf(usdInrExchangeRateService.getRateAsOfDate())
                .updatedAt(settings.getUpdatedAt())
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
        double costUsd = computeCostUsd(modelId, inputTokens, outputTokens);
        double costInr = round(costUsd * liveUsdToInrRate());

        AiUsageEvent event = AiUsageEvent.builder()
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .endpoint(request.getEndpoint())
                .modelId(modelId)
                .courseId(request.getCourseId())
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .costUsd(round(costUsd))
                .costInr(costInr)
                .createdAt(IndiaTime.now())
                .build();
        aiUsageEventRepository.save(event);

        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            User user = userRepository.findById(request.getUserId()).orElse(null);
            if (user != null) {
                resetPeriodIfNeeded(user);
                double used = user.getAiCostUsdThisPeriod() != null ? user.getAiCostUsdThisPeriod() : 0.0;
                user.setAiCostUsdThisPeriod(round(used + costUsd));
                user.setUpdatedAt(IndiaTime.now());
                userRepository.save(user);
            }
        }

        return event;
    }

    public void assertWithinBudget(User user) {
        if (user == null || isUnlimitedForBudget(user)) {
            return;
        }
        PlatformAiSettings settings = loadSettings();
        if (!settings.isAiUsageTrackingEnabled()) {
            return;
        }
        resetPeriodIfNeeded(user);
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

    public AiBudgetDTO getAiBudget(User user) {
        PlatformAiSettings settings = loadSettings();
        double rate = liveUsdToInrRate();
        if (user == null || isUnlimitedForBudget(user)) {
            return AiBudgetDTO.builder()
                    .unlimited(true)
                    .limitReached(false)
                    .build();
        }
        resetPeriodIfNeeded(user);
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
            Double freemiumCap = null;
            Double usedPct = null;
            if (user != null && !isUnlimitedForBudget(user)) {
                freemiumCap = resolveBudgetLimitUsd(user, settings);
                usedPct = freemiumCap > 0 ? round((costUsd / freemiumCap) * 100.0) : 0.0;
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
                    .freemiumBudgetUsd(freemiumCap)
                    .budgetUsedPercent(usedPct)
                    .build());
        }
        rows.sort(Comparator.comparing(AiUsageUserRowDTO::getCostUsd).reversed());
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

        Map<String, AiUsageUserDetailDTO.EndpointBreakdownDTO> breakdownMap = new HashMap<>();
        for (AiUsageEvent event : events) {
            String endpoint = event.getEndpoint() != null ? event.getEndpoint() : "unknown";
            AiUsageUserDetailDTO.EndpointBreakdownDTO existing = breakdownMap.get(endpoint);
            if (existing == null) {
                breakdownMap.put(endpoint, AiUsageUserDetailDTO.EndpointBreakdownDTO.builder()
                        .endpoint(endpoint)
                        .inputTokens(event.getInputTokens())
                        .outputTokens(event.getOutputTokens())
                        .costUsd(event.getCostUsd())
                        .callCount(1)
                        .build());
            } else {
                existing.setInputTokens(existing.getInputTokens() + event.getInputTokens());
                existing.setOutputTokens(existing.getOutputTokens() + event.getOutputTokens());
                existing.setCostUsd(round(existing.getCostUsd() + event.getCostUsd()));
                existing.setCallCount(existing.getCallCount() + 1);
            }
        }

        List<AiUsageUserDetailDTO.EndpointBreakdownDTO> breakdown = new ArrayList<>(breakdownMap.values());
        breakdown.sort(Comparator.comparing(AiUsageUserDetailDTO.EndpointBreakdownDTO::getCostUsd).reversed());

        return AiUsageUserDetailDTO.builder()
                .userId(userId)
                .name(user.getName())
                .email(user.getEmail())
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(total)
                .costUsd(costUsd)
                .costInr(costInr)
                .aiBudget(getAiBudget(user))
                .byEndpoint(breakdown)
                .build();
    }

    private void resetPeriodIfNeeded(User user) {
        LocalDateTime now = IndiaTime.now();
        LocalDateTime periodStart = user.getAiCostPeriodStart();

        // Subscription-aligned period: reset when past currentPeriodEnd
        if (user.getCurrentPeriodEnd() != null
                && user.getAiWalletLimitUsd() != null
                && user.getAiWalletLimitUsd() > 0
                && now.isAfter(user.getCurrentPeriodEnd())) {
            user.setAiCostPeriodStart(now);
            user.setAiCostUsdThisPeriod(0.0);
            return;
        }

        if (periodStart == null || !sameCalendarMonth(periodStart, now)) {
            user.setAiCostPeriodStart(now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0));
            user.setAiCostUsdThisPeriod(0.0);
        }
    }

    private boolean sameCalendarMonth(LocalDateTime a, LocalDateTime b) {
        return a.getYear() == b.getYear() && a.getMonthValue() == b.getMonthValue();
    }

    /**
     * Paid subscription wallets use aiWalletLimitUsd.
     * Spark / freemium uses platform freemiumMonthlyBudgetUsdPerUser.
     * ENTERPRISE, admin roles, legacy null tier, and legacy PAID without a wallet stay unlimited.
     */
    private boolean isUnlimitedForBudget(User user) {
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
        if (user.getAiWalletLimitUsd() != null && user.getAiWalletLimitUsd() > 0) {
            return user.getAiWalletLimitUsd();
        }
        return settings.getFreemiumMonthlyBudgetUsdPerUser();
    }

    /**
     * Prices a single AI call for display (e.g. admin "this image cost ₹X"),
     * without persisting a usage event. Reuses the same rate card + live FX rate.
     */
    public com.prwatech.skillama.dto.AiCostEstimateDTO estimateCost(String modelId, int inputTokens, int outputTokens) {
        int in = Math.max(0, inputTokens);
        int out = Math.max(0, outputTokens);
        double usd = computeCostUsd(modelId != null && !modelId.isBlank() ? modelId : "default", in, out);
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
        JsonNode modelRates = rateCard.path("models").path(modelId);
        if (modelRates.isMissingNode()) {
            modelRates = rateCard.path("default");
        }
        double inputRate = modelRates.path("inputPer1kTokensUsd").asDouble(0.0003);
        double outputRate = modelRates.path("outputPer1kTokensUsd").asDouble(0.0006);
        return (inputTokens / 1000.0) * inputRate + (outputTokens / 1000.0) * outputRate;
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
