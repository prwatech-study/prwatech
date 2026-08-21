package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.TimeBudgetLimitException;
import com.prwatech.skillama.model.PlatformAiSettings;
import com.prwatech.skillama.repository.AiUsageEventRepository;
import com.prwatech.skillama.repository.PlatformAiSettingsRepository;
import com.prwatech.skillama.repository.PlatformEfficiencyAssumptionsRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.TimeWalletAdjustmentEventRepository;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.util.IndiaTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * OR-semantics of the central budget gate for time-based (B2B) seats:
 * access on remaining TIME, or — once time is spent — on a remaining EXPLICIT
 * credit wallet. Unlimited-tier shortcuts must never resurrect a spent time seat.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiUsageServiceTimeGateTest {

    @Mock private AiUsageEventRepository aiUsageEventRepository;
    @Mock private PlatformAiSettingsRepository platformAiSettingsRepository;
    @Mock private PlatformEfficiencyAssumptionsRepository platformEfficiencyAssumptionsRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private UsdInrExchangeRateService usdInrExchangeRateService;
    @Mock private TimeWalletAdjustmentEventRepository timeWalletAdjustmentEventRepository;
    @Mock private com.prwatech.skillama.repository.TimeConsumptionEventRepository timeConsumptionEventRepository;

    private AiUsageService service;

    @BeforeEach
    void setUp() {
        TimeWalletService timeWalletService = new TimeWalletService(
                userRepository, timeWalletAdjustmentEventRepository, timeConsumptionEventRepository);
        service = new AiUsageService(aiUsageEventRepository, platformAiSettingsRepository,
                platformEfficiencyAssumptionsRepository,
                userRepository, new ObjectMapper(), usdInrExchangeRateService, timeWalletService);

        PlatformAiSettings settings = new PlatformAiSettings();
        settings.setAiUsageTrackingEnabled(true);
        settings.setPlatformMonthlyBudgetUsd(1000.0);
        settings.setFreemiumMonthlyBudgetUsdPerUser(0.30);
        when(platformAiSettingsRepository.findById(PlatformAiSettings.SINGLETON_ID))
                .thenReturn(Optional.of(settings));
    }

    private User.UserBuilder base() {
        return User.builder().id("u1").email("u@x.com").role(User.UserRole.USER)
                .aiCostPeriodStart(IndiaTime.now())
                .aiCostUsdThisPeriod(0.0);
    }

    @Test
    void timeSeatWithRemainingTimeIsAllowedEvenOnEnterpriseTier() {
        User user = base().planTier(User.PlanTier.ENTERPRISE)
                .timeAllocatedMinutes(2400.0).timeConsumedMinutes(100.0)
                .build();
        assertDoesNotThrow(() -> service.assertWithinBudget(user));
    }

    @Test
    void exhaustedTimeSeatWithoutExplicitWalletIsBlockedWithTimeError() {
        // ENTERPRISE would normally be unlimited — the spent time seat must still block.
        User user = base().planTier(User.PlanTier.ENTERPRISE)
                .timeAllocatedMinutes(2400.0).timeConsumedMinutes(2400.0)
                .build();
        assertThrows(TimeBudgetLimitException.class, () -> service.assertWithinBudget(user));
    }

    @Test
    void exhaustedTimeSeatFallsBackToExplicitCreditWalletWithHeadroom() {
        User user = base().planTier(User.PlanTier.PAID)
                .timeAllocatedMinutes(2400.0).timeConsumedMinutes(2400.0)
                .aiWalletLimitUsd(5.0).aiCostUsdThisPeriod(1.0)
                .build();
        assertDoesNotThrow(() -> service.assertWithinBudget(user));
    }

    @Test
    void exhaustedTimeAndExhaustedCreditsIsBlockedWithCreditError() {
        User user = base().planTier(User.PlanTier.PAID)
                .timeAllocatedMinutes(2400.0).timeConsumedMinutes(2400.0)
                .aiWalletLimitUsd(5.0).aiCostUsdThisPeriod(5.0)
                .build();
        assertThrows(AiBudgetLimitException.class, () -> service.assertWithinBudget(user));
    }

    @Test
    void nonTimeUsersKeepExistingCreditBehavior() {
        User freemium = base().planTier(User.PlanTier.FREEMIUM).aiCostUsdThisPeriod(0.29).build();
        assertDoesNotThrow(() -> service.assertWithinBudget(freemium));

        User freemiumOver = base().planTier(User.PlanTier.FREEMIUM).aiCostUsdThisPeriod(0.31).build();
        assertThrows(AiBudgetLimitException.class, () -> service.assertWithinBudget(freemiumOver));
    }

    // ---------- learning access (lecture progress gate) ----------

    @Test
    void learningAccessOpenForCreditUsers() {
        User user = base().planTier(User.PlanTier.FREEMIUM).aiCostUsdThisPeriod(99.0).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        assertDoesNotThrow(() -> service.assertLearningAccess("u1"));
    }

    @Test
    void learningAccessBlockedForSpentTimeSeatWithoutCredits() {
        User user = base().planTier(User.PlanTier.ENTERPRISE)
                .timeAllocatedMinutes(600.0).timeConsumedMinutes(600.0)
                .build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        assertThrows(TimeBudgetLimitException.class, () -> service.assertLearningAccess("u1"));
    }

    @Test
    void learningAccessAllowedForSpentTimeSeatWithCreditHeadroom() {
        User user = base().planTier(User.PlanTier.PAID)
                .timeAllocatedMinutes(600.0).timeConsumedMinutes(600.0)
                .aiWalletLimitUsd(5.0).aiCostUsdThisPeriod(1.0)
                .build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        assertDoesNotThrow(() -> service.assertLearningAccess("u1"));
    }

    @Test
    void learningAccessOpenWhenUserMissing() {
        when(userRepository.findById("u1")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.assertLearningAccess("u1"));
        assertDoesNotThrow(() -> service.assertLearningAccess(null));
    }
}
