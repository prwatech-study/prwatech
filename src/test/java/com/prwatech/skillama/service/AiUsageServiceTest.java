package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.dto.AiBudgetDTO;
import com.prwatech.skillama.dto.AiUsageModuleBreakdownDTO;
import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.dto.AiUsageSettingsDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiUsageServiceTest {

    @Mock private AiUsageEventRepository aiUsageEventRepository;
    @Mock private PlatformAiSettingsRepository platformAiSettingsRepository;
    @Mock private com.prwatech.skillama.repository.PlatformEfficiencyAssumptionsRepository platformEfficiencyAssumptionsRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private UsdInrExchangeRateService usdInrExchangeRateService;
    @Mock private TimeWalletService timeWalletService;

    private AiUsageService service;

    @BeforeEach
    void setUp() {
        service = new AiUsageService(aiUsageEventRepository, platformAiSettingsRepository,
                platformEfficiencyAssumptionsRepository,
                userRepository, new ObjectMapper(), usdInrExchangeRateService, timeWalletService);
        service.loadRateCard(); // ensures rateCard is non-null for cost computation
        when(usdInrExchangeRateService.getUsdToInrRate()).thenReturn(83.0);
        when(platformAiSettingsRepository.findById(PlatformAiSettings.SINGLETON_ID))
                .thenReturn(Optional.of(trackingSettings(true, 0.5)));
    }

    private PlatformAiSettings trackingSettings(boolean tracking, double freemiumBudget) {
        PlatformAiSettings s = new PlatformAiSettings();
        s.setAiUsageTrackingEnabled(tracking);
        s.setPlatformMonthlyBudgetUsd(1000.0);
        s.setFreemiumMonthlyBudgetUsdPerUser(freemiumBudget);
        return s;
    }

    private User freemium(double usedUsd) {
        return User.builder().id("u1").email("u@x.com").role(User.UserRole.USER)
                .planTier(User.PlanTier.FREEMIUM)
                .aiCostPeriodStart(IndiaTime.now())   // current month → no reset
                .aiCostUsdThisPeriod(usedUsd)
                .build();
    }

    // ---------- internal api key ----------

    @Test
    void internalApiKeyValidationRequiresConfiguredKey() {
        assertFalse(service.isValidInternalApiKey("anything")); // no key configured
        ReflectionTestUtils.setField(service, "internalApiKey", "secret");
        assertTrue(service.isValidInternalApiKey("secret"));
        assertFalse(service.isValidInternalApiKey("wrong"));
        assertFalse(service.isValidInternalApiKey(null));
    }

    // ---------- assertWithinBudget ----------

    @Test
    void nullUserIsWithinBudget() {
        service.assertWithinBudget(null); // no throw
        assertTrue(service.isWithinBudget(null));
    }

    @Test
    void adminIsUnlimited() {
        User admin = User.builder().id("a").role(User.UserRole.ADMIN).planTier(User.PlanTier.FREEMIUM).build();
        service.assertWithinBudget(admin); // no throw
    }

    @Test
    void legacyNullTierIsUnlimited() {
        User legacy = User.builder().id("l").role(User.UserRole.USER).planTier(null).build();
        assertTrue(service.isWithinBudget(legacy));
    }

    @Test
    void trackingDisabledSkipsBudgetCheck() {
        when(platformAiSettingsRepository.findById(PlatformAiSettings.SINGLETON_ID))
                .thenReturn(Optional.of(trackingSettings(false, 0.5)));
        service.assertWithinBudget(freemium(999.0)); // no throw even though way over
    }

    @Test
    void freemiumUnderBudgetPasses() {
        service.assertWithinBudget(freemium(0.1));
        assertTrue(service.isWithinBudget(freemium(0.1)));
    }

    @Test
    void freemiumAtOrOverBudgetThrows() {
        AiBudgetLimitException ex = assertThrows(AiBudgetLimitException.class,
                () -> service.assertWithinBudget(freemium(0.6)));
        assertEquals(0.5, ex.getAiCostLimitUsd());
        assertFalse(service.isWithinBudget(freemium(0.5))); // exactly at limit is blocked
    }

    @Test
    void paidWalletBudgetIsUsedWhenSet() {
        User paid = User.builder().id("p").role(User.UserRole.USER).planTier(User.PlanTier.PAID)
                .aiWalletLimitUsd(18.75).aiCostPeriodStart(IndiaTime.now()).aiCostUsdThisPeriod(20.0).build();
        assertThrows(AiBudgetLimitException.class, () -> service.assertWithinBudget(paid));
    }

    // ---------- referral bonus stacks on top of whichever base applies ----------

    @Test
    void freemiumReferralBonusAddsToPlatformBudget() {
        // $0.50 platform freemium budget + $0.25 referrer reward = $0.75 effective
        User u = freemium(0.60);
        u.setReferralBonusUsd(0.25);
        service.assertWithinBudget(u); // 0.60 < 0.75 → no throw
        assertTrue(service.isWithinBudget(u));

        AiBudgetDTO dto = service.getAiBudget(u);
        assertEquals(0.75, dto.getLimitUsd());
        assertEquals(0.25, dto.getReferralBonusUsd());
        assertEquals(0.15, dto.getRemainingUsd());
        assertFalse(dto.getLimitReached());
    }

    @Test
    void freemiumWithReferralBonusStillBlocksOnceCombinedLimitReached() {
        User u = freemium(0.75);
        u.setReferralBonusUsd(0.25);
        AiBudgetLimitException ex = assertThrows(AiBudgetLimitException.class,
                () -> service.assertWithinBudget(u));
        assertEquals(0.75, ex.getAiCostLimitUsd());
    }

    @Test
    void walletOverrideAndReferralBonusAddTogether() {
        // aiWalletLimitUsd override ($18.75) + referral bonus ($0.50) = $19.25
        User paid = User.builder().id("p").role(User.UserRole.USER).planTier(User.PlanTier.PAID)
                .aiWalletLimitUsd(18.75).referralBonusUsd(0.50)
                .aiCostPeriodStart(IndiaTime.now()).aiCostUsdThisPeriod(19.0).build();

        service.assertWithinBudget(paid); // 19.00 < 19.25 → no throw
        AiBudgetDTO dto = service.getAiBudget(paid);
        assertEquals(19.25, dto.getLimitUsd());
        assertEquals(0.50, dto.getReferralBonusUsd());
        assertEquals(0.25, dto.getRemainingUsd());

        // Without the bonus the same spend would already be over the wallet.
        paid.setReferralBonusUsd(0.0);
        assertThrows(AiBudgetLimitException.class, () -> service.assertWithinBudget(paid));
    }

    @Test
    void nullReferralBonusIsTreatedAsZero() {
        User u = freemium(0.2);
        u.setReferralBonusUsd(null);
        AiBudgetDTO dto = service.getAiBudget(u);
        assertEquals(0.5, dto.getLimitUsd());
        assertEquals(0.0, dto.getReferralBonusUsd());
    }

    // ---------- wallet base resolution (used by admin wallet adjustments) ----------

    @Test
    void resolveWalletBaseUsdExcludesReferralBonus() {
        User u = freemium(0.0);
        u.setReferralBonusUsd(1.25);
        assertEquals(0.5, service.resolveWalletBaseUsd(u)); // platform freemium budget only

        u.setAiWalletLimitUsd(9.0);
        assertEquals(9.0, service.resolveWalletBaseUsd(u)); // wallet override wins as the base
    }

    @Test
    void isUnlimitedForBudgetIsPubliclyQueryable() {
        assertTrue(service.isUnlimitedForBudget(
                User.builder().id("e").role(User.UserRole.USER).planTier(User.PlanTier.ENTERPRISE).build()));
        assertFalse(service.isUnlimitedForBudget(freemium(0.0)));
    }

    // ---------- getAiBudget ----------

    @Test
    void getAiBudgetUnlimitedForEnterprise() {
        User ent = User.builder().id("e").role(User.UserRole.USER).planTier(User.PlanTier.ENTERPRISE).build();
        AiBudgetDTO dto = service.getAiBudget(ent);
        assertTrue(dto.getUnlimited());
        assertFalse(dto.getLimitReached());
        assertNull(dto.getLimitUsd());
    }

    @Test
    void getAiBudgetComputesFreemiumRemainingAndInr() {
        AiBudgetDTO dto = service.getAiBudget(freemium(0.2));
        assertFalse(dto.getUnlimited());
        assertEquals(0.5, dto.getLimitUsd());
        assertEquals(0.2, dto.getUsedUsd());
        assertEquals(0.3, dto.getRemainingUsd());
        assertEquals(0.5 * 83.0, dto.getLimitInr());
        assertFalse(dto.getLimitReached());
    }

    @Test
    void getAiBudgetFlagsLimitReached() {
        AiBudgetDTO dto = service.getAiBudget(freemium(0.5));
        assertTrue(dto.getLimitReached());
        assertEquals(0.0, dto.getRemainingUsd());
    }

    // ---------- updateSettings ----------

    @Test
    void updateSettingsRejectsNullBody() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(null, "owner"));
    }

    @Test
    void updateSettingsClampsNegativeBudgetsToZero() {
        when(platformAiSettingsRepository.save(any(PlatformAiSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        UpdateAiUsageSettingsDTO body = new UpdateAiUsageSettingsDTO();
        body.setPlatformMonthlyBudgetUsd(-100.0);
        body.setFreemiumMonthlyBudgetUsdPerUser(-1.0);
        body.setAiUsageTrackingEnabled(false);

        AiUsageSettingsDTO dto = service.updateSettings(body, "owner");

        assertEquals(0.0, dto.getPlatformMonthlyBudgetUsd());
        assertEquals(0.0, dto.getFreemiumMonthlyBudgetUsdPerUser());
        assertFalse(dto.isAiUsageTrackingEnabled());
    }

    // ---------- recordUsage ----------

    @Test
    void recordUsageReturnsNullWhenTrackingDisabled() {
        when(platformAiSettingsRepository.findById(PlatformAiSettings.SINGLETON_ID))
                .thenReturn(Optional.of(trackingSettings(false, 0.5)));
        AiUsageRecordRequestDTO req = new AiUsageRecordRequestDTO();
        req.setEndpoint("/chat");
        req.setUserId("u1");
        assertNull(service.recordUsage(req));
        verify(aiUsageEventRepository, never()).save(any());
    }

    @Test
    void recordUsageRequiresEndpoint() {
        AiUsageRecordRequestDTO req = new AiUsageRecordRequestDTO();
        req.setUserId("u1");
        assertThrows(IllegalArgumentException.class, () -> service.recordUsage(req));
    }

    @Test
    void recordUsageRequiresUserOrSession() {
        AiUsageRecordRequestDTO req = new AiUsageRecordRequestDTO();
        req.setEndpoint("/chat");
        assertThrows(IllegalArgumentException.class, () -> service.recordUsage(req));
    }

    @Test
    void recordUsageSavesEventAndAddsToUserPeriodCost() {
        AiUsageRecordRequestDTO req = new AiUsageRecordRequestDTO();
        req.setEndpoint("/chat");
        req.setUserId("u1");
        req.setInputTokens(1000);
        req.setOutputTokens(1000);
        when(aiUsageEventRepository.save(any(AiUsageEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        User user = freemium(0.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AiUsageEvent event = service.recordUsage(req);

        assertEquals(1000, event.getInputTokens());
        assertEquals(2000, event.getTotalTokens());
        assertTrue(event.getCostUsd() > 0);
        // user period cost incremented by the event cost
        assertTrue(user.getAiCostUsdThisPeriod() > 0);
        verify(aiUsageEventRepository).save(any(AiUsageEvent.class));
        verify(userRepository).save(user);
    }

    // ---------- getUserModuleBreakdown ----------

    private AiUsageEvent event(String endpoint, double costUsd) {
        return AiUsageEvent.builder()
                .userId("u1")
                .endpoint(endpoint)
                .costUsd(costUsd)
                .createdAt(IndiaTime.now())
                .build();
    }

    @Test
    void getUserModuleBreakdownThrowsWhenUserNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.getUserModuleBreakdown("missing"));
    }

    @Test
    void getUserModuleBreakdownIsReadOnlyLikeGetAiBudget() {
        User user = freemium(0.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(aiUsageEventRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq("u1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        service.getUserModuleBreakdown("u1");

        // resetPeriodIfNeeded is applied in-memory only to pick the right window —
        // persistence still happens on the next recordUsage call, not here.
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserModuleBreakdownReturnsEmptyWhenNoUsageThisPeriod() {
        User user = freemium(0.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(aiUsageEventRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq("u1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        AiUsageModuleBreakdownDTO dto = service.getUserModuleBreakdown("u1");

        assertEquals(0.0, dto.getTotalCostUsd());
        assertTrue(dto.getByModule().isEmpty());
    }

    @Test
    void getUserModuleBreakdownGroupsKnownEndpointsIntoCoarseModules() {
        User user = freemium(0.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(aiUsageEventRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq("u1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        event("debug_assist", 0.010),
                        event("code_execution_assist", 0.005),
                        event("generate_practical_code", 0.005),
                        event("chat_ask", 0.003),
                        event("ai_mentor_ask", 0.002),
                        // Regression coverage: generate_exam was initially missing from
                        // ENDPOINT_MODULE_MAP and silently fell into "Other".
                        event("generate_exam", 0.002),
                        event("lecture_generation", 0.020),
                        event("some_future_endpoint_not_mapped_yet", 0.001)
                ));

        AiUsageModuleBreakdownDTO dto = service.getUserModuleBreakdown("u1");

        assertEquals(0.048, dto.getTotalCostUsd(), 1e-9);

        java.util.Map<String, AiUsageModuleBreakdownDTO.ModuleUsageDTO> byModule =
                dto.getByModule().stream().collect(java.util.stream.Collectors.toMap(
                        AiUsageModuleBreakdownDTO.ModuleUsageDTO::getModule, m -> m));

        assertEquals(0.010, byModule.get("Debug").getCostUsd(), 1e-9);
        assertEquals(1, byModule.get("Debug").getCallCount());

        assertEquals(0.010, byModule.get("Code Execution").getCostUsd(), 1e-9); // 0.005 + 0.005
        assertEquals(2, byModule.get("Code Execution").getCallCount());

        // chat_ask + ai_mentor_ask + generate_exam all fold into Ai-Tutor
        assertEquals(0.007, byModule.get("Ai-Tutor").getCostUsd(), 1e-9);
        assertEquals(3, byModule.get("Ai-Tutor").getCallCount());

        assertEquals(0.020, byModule.get("Lecture Generation").getCostUsd(), 1e-9);

        assertEquals(0.001, byModule.get("Other").getCostUsd(), 1e-9);

        // Sorted by cost descending.
        List<String> order = dto.getByModule().stream()
                .map(AiUsageModuleBreakdownDTO.ModuleUsageDTO::getModule)
                .toList();
        assertEquals("Lecture Generation", order.get(0));
    }

    @Test
    void getUserModuleBreakdownPercentagesAddUpToTotal() {
        User user = freemium(0.0);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(aiUsageEventRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq("u1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(event("debug_assist", 0.03), event("lecture_generation", 0.01)));

        AiUsageModuleBreakdownDTO dto = service.getUserModuleBreakdown("u1");

        double percentSum = dto.getByModule().stream()
                .mapToDouble(AiUsageModuleBreakdownDTO.ModuleUsageDTO::getPercentOfTotal)
                .sum();
        assertEquals(100.0, percentSum, 1e-9);
    }
}
