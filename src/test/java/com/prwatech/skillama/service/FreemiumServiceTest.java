package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.skillama.dto.AiBudgetDTO;
import com.prwatech.skillama.dto.ConsumeQueryRequestDTO;
import com.prwatech.skillama.dto.CreditAdjustRequestDTO;
import com.prwatech.skillama.dto.FreemiumOfferingDTO;
import com.prwatech.skillama.dto.FreemiumStatusDTO;
import com.prwatech.skillama.dto.WalletAdjustResultDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.PlatformAiSettings;
import com.prwatech.skillama.model.QueryActivityLog;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.QueryActivityLogRepository;
import com.prwatech.skillama.repository.ReferralConversionEventRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreemiumServiceTest {

    @Mock private SkillamaUserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private QueryActivityLogRepository queryActivityLogRepository;
    @Mock private PasswordEncode passwordEncode;
    @Mock private UserCourseAccessService userCourseAccessService;
    @Mock private UserContactService userContactService;
    @Mock private AiUsageService aiUsageService;
    @Mock private ReferralConversionEventRepository referralConversionEventRepository;

    private FreemiumService service;

    @BeforeEach
    void setUp() {
        service = new FreemiumService(userRepository, courseRepository, queryActivityLogRepository,
                passwordEncode, userCourseAccessService, userContactService, aiUsageService,
                referralConversionEventRepository);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiUsageService.getAiBudget(any())).thenReturn(AiBudgetDTO.builder().build());
        // Referral reward is owner-tunable now (was a fixed 0.25 constant) — tests assert
        // against this explicit value rather than whatever the current production default is.
        PlatformAiSettings settings = new PlatformAiSettings();
        settings.setReferralRewardUsd(0.25);
        when(aiUsageService.loadSettings()).thenReturn(settings);
    }

    private User freemiumUser() {
        return User.builder().id("u1").email("u@x.com").planTier(User.PlanTier.FREEMIUM)
                .emailVerified(true).referralBonusUsd(0.0).build();
    }

    // ---------- static validation ----------

    @Test
    void validatePhoneRejectsNullShortAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> FreemiumService.validatePhone(null));
        assertThrows(IllegalArgumentException.class, () -> FreemiumService.validatePhone(""));
        assertThrows(IllegalArgumentException.class, () -> FreemiumService.validatePhone("12345"));
    }

    @Test
    void validatePhoneAcceptsTenDigits() {
        FreemiumService.validatePhone("9876543210"); // no throw
    }

    @Test
    void normalizePhonePrefixesIndiaCodeForTenDigits() {
        assertEquals("+919876543210", FreemiumService.normalizePhone("9876543210"));
        assertEquals("+14155550123", FreemiumService.normalizePhone("+14155550123"));
    }

    @Test
    void normalizeReferralCodeUppercasesAndTrims() {
        assertEquals("SKILL-ABC", FreemiumService.normalizeReferralCode("  skill-abc "));
        assertNull(FreemiumService.normalizeReferralCode(null));
    }

    @Test
    void generateReferralCodeHasSkillPrefix() {
        assertTrue(FreemiumService.generateReferralCode().startsWith("SKILL-"));
    }

    // ---------- public offering ----------

    @Test
    void publicOfferingExposesReferrerRewardAndModules() {
        FreemiumOfferingDTO dto = service.getPublicOffering();
        // Reflects the current owner-tunable settings value (stubbed to 0.25 in setUp).
        assertEquals(0.25, dto.getReferrerRewardUsd());
        assertTrue(dto.getBaseModules().containsAll(FreemiumService.FREEMIUM_BASE_MODULES));
    }

    // ---------- unlimited / module gating ----------

    @Test
    void legacyPaidEnterpriseAreUnlimited() {
        assertTrue(service.isUnlimited(User.builder().build())); // planTier null = legacy
        assertTrue(service.isUnlimited(User.builder().planTier(User.PlanTier.PAID).build()));
        assertTrue(service.isUnlimited(User.builder().planTier(User.PlanTier.ENTERPRISE).build()));
        assertFalse(service.isUnlimited(freemiumUser()));
    }

    @Test
    void hasModuleTrueForUnlimitedRegardlessOfList() {
        User paid = User.builder().planTier(User.PlanTier.PAID).build();
        assertTrue(service.hasModule(paid, "anything"));
    }

    @Test
    void hasModuleChecksEnabledListCaseInsensitiveForFreemium() {
        User u = freemiumUser();
        u.setEnabledModules(List.of("Ai-Tutor", "Debug"));
        assertTrue(service.hasModule(u, "ai-tutor"));
        assertFalse(service.hasModule(u, "Courses"));
    }

    @Test
    void hasUnlimitedWalletDelegatesToAiUsageService() {
        User u = freemiumUser();
        when(aiUsageService.isUnlimitedForBudget(u)).thenReturn(true);
        assertTrue(service.hasUnlimitedWallet(u));
    }

    // ---------- consumeQuery (wallet is the only enforcement) ----------

    @Test
    void consumeQueryEnforcesOnlyTheDollarWallet() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        service.consumeQuery("u1", new ConsumeQueryRequestDTO());

        verify(aiUsageService).assertWithinBudget(u);
        verify(queryActivityLogRepository).save(any(QueryActivityLog.class));
        // No query-count mutation of the user record any more
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void consumeQueryPropagatesBudgetLimitAndDoesNotLogActivity() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        doThrow(new AiBudgetLimitException("AI budget limit reached", 0.75, 0.75))
                .when(aiUsageService).assertWithinBudget(u);

        assertThrows(AiBudgetLimitException.class,
                () -> service.consumeQuery("u1", new ConsumeQueryRequestDTO()));
        verify(queryActivityLogRepository, never()).save(any());
    }

    @Test
    void consumeQueryUnlimitedUserStillLogs() {
        User u = User.builder().id("u1").planTier(User.PlanTier.PAID).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        service.consumeQuery("u1", new ConsumeQueryRequestDTO());

        verify(aiUsageService).assertWithinBudget(u);
        verify(queryActivityLogRepository).save(any(QueryActivityLog.class));
    }

    // ---------- referral: the REFERRER is rewarded, not the referee ----------

    @Test
    void applyReferralRewardsTheReferrerWithQuarterDollar() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        User referrer = User.builder().id("ref").email("r@x.com").referralCode("SKILL-REF")
                .referralBonusUsd(0.0).build();
        when(userRepository.findByReferralCode("SKILL-REF")).thenReturn(Optional.of(referrer));

        FreemiumStatusDTO dto = service.applyReferral("u1", "skill-ref");

        assertEquals("SKILL-REF", u.getReferredBy());
        // Referrer earns the reward...
        assertEquals(0.25, referrer.getReferralBonusUsd());
        verify(userRepository).save(referrer);
        // ...the referee gets nothing.
        assertEquals(0.0, u.getReferralBonusUsd());
        assertEquals(0.0, dto.getReferralBonusUsd());
    }

    @Test
    void referrerRewardStacksWithoutLimitAcrossRepeatReferrals() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        User referrer = User.builder().id("ref").email("r@x.com").referralCode("SKILL-REF")
                .referralBonusUsd(1.50).build(); // already referred 6 people
        when(userRepository.findByReferralCode("SKILL-REF")).thenReturn(Optional.of(referrer));

        service.applyReferral("u1", "SKILL-REF");

        assertEquals(1.75, referrer.getReferralBonusUsd());
    }

    @Test
    void referrerRewardTreatsNullBonusAsZero() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        User referrer = User.builder().id("ref").email("r@x.com").referralCode("SKILL-REF").build();
        referrer.setReferralBonusUsd(null);
        when(userRepository.findByReferralCode("SKILL-REF")).thenReturn(Optional.of(referrer));

        service.applyReferral("u1", "SKILL-REF");

        assertEquals(0.25, referrer.getReferralBonusUsd());
    }

    @Test
    void applyReferralRejectsAlreadyApplied() {
        User u = freemiumUser();
        u.setReferredBy("SKILL-OLD");
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        assertThrows(IllegalStateException.class, () -> service.applyReferral("u1", "SKILL-NEW"));
    }

    @Test
    void applyReferralRejectsInvalidCode() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findByReferralCode("SKILL-GHOST")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.applyReferral("u1", "skill-ghost"));
    }

    @Test
    void applyReferralRejectsOwnCodeAndPaysNothing() {
        User u = freemiumUser();
        u.setReferralCode("SKILL-SELF");
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findByReferralCode("SKILL-SELF")).thenReturn(Optional.of(u));

        assertThrows(IllegalArgumentException.class, () -> service.applyReferral("u1", "SKILL-SELF"));

        assertEquals(0.0, u.getReferralBonusUsd());
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------- referral code sharing requires a verified email ----------

    @Test
    void getReferralCodeRejectsUnverifiedEmail() {
        User u = freemiumUser();
        u.setEmailVerified(false);
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        assertThrows(ForbiddenException.class, () -> service.getReferralCode("u1"));

        u.setEmailVerified(null);
        assertThrows(ForbiddenException.class, () -> service.getReferralCode("u1"));
    }

    @Test
    void getReferralCodeGeneratesForVerifiedUser() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        String code = service.getReferralCode("u1");
        assertTrue(code.startsWith("SKILL-"));
    }

    // ---------- updateUserPlan ----------

    @Test
    void updateUserPlanRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> service.updateUserPlan("u1", null));
    }

    @Test
    void updateUserPlanToPaidGrantsPremiumModules() {
        User u = freemiumUser();
        u.setPhone("+919876543210");
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        FreemiumStatusDTO dto = service.updateUserPlan("u1", User.PlanTier.PAID);

        assertEquals(User.PlanTier.PAID, u.getPlanTier());
        assertTrue(u.getEnabledModules().containsAll(FreemiumService.PREMIUM_MODULES));
        // unlimitedQueries now mirrors wallet enforcement, not a query count
        assertFalse(dto.getUnlimitedQueries());
    }

    // ---------- adjustWalletBalance (USD) ----------

    @Test
    void adjustWalletRequiresReason() {
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setDeltaUsd(2.0);
        assertThrows(IllegalArgumentException.class, () -> service.adjustWalletBalance("u1", req, "admin"));
    }

    @Test
    void adjustWalletRejectsUnmeteredUser() {
        User u = User.builder().id("u1").planTier(User.PlanTier.ENTERPRISE).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findById("admin"))
                .thenReturn(Optional.of(User.builder().id("admin").email("a@x.com").build()));
        when(aiUsageService.isUnlimitedForBudget(u)).thenReturn(true);
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setDeltaUsd(5.0);
        req.setReason("bonus");

        assertThrows(IllegalStateException.class, () -> service.adjustWalletBalance("u1", req, "admin"));
    }

    @Test
    void adjustWalletPositiveDeltaTopsUpAndPersists() {
        User u = freemiumUser();
        u.setReferralBonusUsd(0.25);
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findById("admin"))
                .thenReturn(Optional.of(User.builder().id("admin").email("a@x.com").build()));
        when(aiUsageService.isUnlimitedForBudget(u)).thenReturn(false);
        when(aiUsageService.resolveWalletBaseUsd(u)).thenReturn(0.50);
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setDeltaUsd(2.0);
        req.setReason("promo");

        WalletAdjustResultDTO dto = service.adjustWalletBalance("u1", req, "admin");

        assertEquals(2.50, u.getAiWalletLimitUsd()); // 0.50 + 2.00 persisted on the user
        verify(userRepository).save(u);
        assertEquals(0.50, dto.getWalletBeforeUsd());
        assertEquals(2.50, dto.getWalletAfterUsd());
        assertEquals(2.0, dto.getDeltaUsd());
        // referral bonus untouched and stacked on top for enforcement
        assertEquals(0.25, u.getReferralBonusUsd());
        assertEquals(0.25, dto.getReferralBonusUsd());
        assertEquals(2.75, dto.getEffectiveLimitUsd());
        assertEquals("promo", dto.getReason());
    }

    @Test
    void adjustWalletNegativeDeltaNeverGoesBelowZero() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findById("admin"))
                .thenReturn(Optional.of(User.builder().id("admin").email("a@x.com").build()));
        when(aiUsageService.resolveWalletBaseUsd(u)).thenReturn(0.50);
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setDeltaUsd(-10.0);
        req.setReason("clawback");

        WalletAdjustResultDTO dto = service.adjustWalletBalance("u1", req, "admin");

        assertEquals(0.0, u.getAiWalletLimitUsd());
        assertEquals(0.0, dto.getWalletAfterUsd());
    }

    @Test
    void adjustWalletNewLimitSetsAbsoluteBase() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findById("admin"))
                .thenReturn(Optional.of(User.builder().id("admin").email("a@x.com").build()));
        when(aiUsageService.resolveWalletBaseUsd(u)).thenReturn(0.50);
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setNewLimitUsd(7.5);
        req.setReason("manual override");

        WalletAdjustResultDTO dto = service.adjustWalletBalance("u1", req, "admin");

        assertEquals(7.5, u.getAiWalletLimitUsd());
        assertEquals(7.5, dto.getWalletAfterUsd());
    }

    @Test
    void adjustWalletMissingAdminThrowsNotFound() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findById("ghostAdmin")).thenReturn(Optional.empty());
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setDeltaUsd(1.0);
        req.setReason("x");

        assertThrows(ResourceNotFoundException.class,
                () -> service.adjustWalletBalance("u1", req, "ghostAdmin"));
    }

    @Test
    void requireUserMissingThrowsNotFound() {
        when(userRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.consumeQuery("nope", new ConsumeQueryRequestDTO()));
    }
}
