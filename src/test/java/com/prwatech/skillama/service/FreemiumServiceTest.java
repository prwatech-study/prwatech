package com.prwatech.skillama.service;

import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.skillama.dto.AiBudgetDTO;
import com.prwatech.skillama.dto.ConsumeQueryRequestDTO;
import com.prwatech.skillama.dto.CreditAdjustRequestDTO;
import com.prwatech.skillama.dto.CreditAdjustmentLogDTO;
import com.prwatech.skillama.dto.FreemiumOfferingDTO;
import com.prwatech.skillama.dto.FreemiumStatusDTO;
import com.prwatech.skillama.dto.QueryCreditsDTO;
import com.prwatech.skillama.exception.QueryCreditLimitException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CreditAdjustmentLog;
import com.prwatech.skillama.model.QueryActivityLog;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.CreditAdjustmentLogRepository;
import com.prwatech.skillama.repository.QueryActivityLogRepository;
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
    @Mock private CreditAdjustmentLogRepository creditAdjustmentLogRepository;
    @Mock private UserContactService userContactService;
    @Mock private AiUsageService aiUsageService;

    private FreemiumService service;

    @BeforeEach
    void setUp() {
        service = new FreemiumService(userRepository, courseRepository, queryActivityLogRepository,
                passwordEncode, userCourseAccessService, creditAdjustmentLogRepository,
                userContactService, aiUsageService);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiUsageService.getAiBudget(any())).thenReturn(AiBudgetDTO.builder().build());
    }

    private User freemiumUser() {
        return User.builder().id("u1").email("u@x.com").planTier(User.PlanTier.FREEMIUM)
                .queryCreditsUsed(0).queryCreditsLimit(FreemiumService.FREEMIUM_QUERY_LIMIT).build();
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
    void publicOfferingExposesLimitsAndBonus() {
        FreemiumOfferingDTO dto = service.getPublicOffering();
        assertEquals(FreemiumService.FREEMIUM_QUERY_LIMIT, dto.getQueryLimit());
        assertEquals(FreemiumService.REFERRAL_QUERY_BONUS, dto.getReferralQueryBonus());
        assertEquals(FreemiumService.FREEMIUM_QUERY_LIMIT + FreemiumService.REFERRAL_QUERY_BONUS,
                dto.getQueryLimitWithReferral());
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
    void remainingQueriesForFreemiumAndUnlimited() {
        User u = freemiumUser();
        u.setQueryCreditsUsed(45);
        assertEquals(5, service.remainingQueries(u));
        assertEquals(Integer.MAX_VALUE,
                service.remainingQueries(User.builder().planTier(User.PlanTier.PAID).build()));
    }

    @Test
    void getQueryCreditsUnlimitedHasNullLimit() {
        QueryCreditsDTO dto = service.getQueryCredits(User.builder()
                .planTier(User.PlanTier.PAID).queryCreditsUsed(3).build());
        assertNull(dto.getLimit());
    }

    // ---------- consumeQuery (credit enforcement) ----------

    @Test
    void consumeQueryIncrementsUsedForFreemium() {
        User u = freemiumUser();
        u.setQueryCreditsUsed(10);
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        FreemiumStatusDTO dto = service.consumeQuery("u1", new ConsumeQueryRequestDTO());

        assertEquals(11, u.getQueryCreditsUsed());
        assertEquals(11, dto.getQueryCreditsUsed());
        verify(aiUsageService).assertWithinBudget(u);
        verify(queryActivityLogRepository).save(any(QueryActivityLog.class));
    }

    @Test
    void consumeQueryThrowsWhenLimitReached() {
        User u = freemiumUser();
        u.setQueryCreditsUsed(FreemiumService.FREEMIUM_QUERY_LIMIT); // 50/50
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        QueryCreditLimitException ex = assertThrows(QueryCreditLimitException.class,
                () -> service.consumeQuery("u1", new ConsumeQueryRequestDTO()));
        assertEquals(FreemiumService.FREEMIUM_QUERY_LIMIT, ex.getQueryCreditsLimit());
        // No activity logged and no increment on rejection
        verify(queryActivityLogRepository, never()).save(any());
        assertEquals(FreemiumService.FREEMIUM_QUERY_LIMIT, u.getQueryCreditsUsed());
    }

    @Test
    void consumeQueryUnlimitedDoesNotIncrementButStillLogs() {
        User u = User.builder().id("u1").planTier(User.PlanTier.PAID).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        service.consumeQuery("u1", new ConsumeQueryRequestDTO());

        verify(aiUsageService).assertWithinBudget(u);
        verify(userRepository, never()).save(any()); // no credit mutation for unlimited
        verify(queryActivityLogRepository).save(any(QueryActivityLog.class));
    }

    // ---------- referral ----------

    @Test
    void applyReferralGrantsBonusLimitAndModules() {
        User u = freemiumUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        User referrer = User.builder().id("ref").email("r@x.com").referralCode("SKILL-REF").build();
        when(userRepository.findByReferralCode("SKILL-REF")).thenReturn(Optional.of(referrer));

        FreemiumStatusDTO dto = service.applyReferral("u1", "skill-ref");

        assertEquals("SKILL-REF", u.getReferredBy());
        assertEquals(FreemiumService.FREEMIUM_QUERY_LIMIT + FreemiumService.REFERRAL_QUERY_BONUS,
                dto.getQueryCreditsLimit());
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
    void applyReferralRejectsOwnCode() {
        User u = freemiumUser();
        u.setReferralCode("SKILL-SELF");
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findByReferralCode("SKILL-SELF")).thenReturn(Optional.of(u));
        assertThrows(IllegalArgumentException.class, () -> service.applyReferral("u1", "SKILL-SELF"));
    }

    // ---------- updateUserPlan ----------

    @Test
    void updateUserPlanRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> service.updateUserPlan("u1", null));
    }

    @Test
    void updateUserPlanToPaidGrantsUnlimitedAndPremiumModules() {
        User u = freemiumUser();
        u.setPhone("+919876543210");
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));

        FreemiumStatusDTO dto = service.updateUserPlan("u1", User.PlanTier.PAID);

        assertEquals(User.PlanTier.PAID, u.getPlanTier());
        assertNull(u.getQueryCreditsLimit());
        assertTrue(dto.getUnlimitedQueries());
        assertTrue(u.getEnabledModules().containsAll(FreemiumService.PREMIUM_MODULES));
    }

    // ---------- adjustQueryCredits ----------

    @Test
    void adjustCreditsRequiresReason() {
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setDelta(100);
        assertThrows(IllegalArgumentException.class, () -> service.adjustQueryCredits("u1", req, "admin"));
    }

    @Test
    void adjustCreditsRejectsUnlimitedUser() {
        User u = User.builder().id("u1").planTier(User.PlanTier.PAID).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findById("admin")).thenReturn(Optional.of(User.builder().id("admin").email("a@x.com").build()));
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setDelta(50);
        req.setReason("bonus");
        assertThrows(IllegalStateException.class, () -> service.adjustQueryCredits("u1", req, "admin"));
    }

    @Test
    void adjustCreditsPositiveDeltaRaisesLimitPreservingUsed() {
        User u = freemiumUser();
        u.setQueryCreditsUsed(29);
        u.setQueryCreditsLimit(50);
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findById("admin")).thenReturn(Optional.of(User.builder().id("admin").email("a@x.com").build()));
        when(creditAdjustmentLogRepository.save(any(CreditAdjustmentLog.class))).thenAnswer(inv -> {
            CreditAdjustmentLog l = inv.getArgument(0);
            l.setId("log1");
            return l;
        });
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setDelta(100);
        req.setReason("promo");

        CreditAdjustmentLogDTO dto = service.adjustQueryCredits("u1", req, "admin");

        assertEquals(150, u.getQueryCreditsLimit()); // 50 + 100
        assertEquals(29, u.getQueryCreditsUsed());   // used preserved
        assertEquals(29, dto.getBalanceAfterUsed());
        assertEquals(150, dto.getLimitAfter());
    }

    @Test
    void adjustCreditsNewLimitNeverBelowUsed() {
        User u = freemiumUser();
        u.setQueryCreditsUsed(40);
        u.setQueryCreditsLimit(50);
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.findById("admin")).thenReturn(Optional.of(User.builder().id("admin").email("a@x.com").build()));
        when(creditAdjustmentLogRepository.save(any(CreditAdjustmentLog.class))).thenAnswer(inv -> inv.getArgument(0));
        CreditAdjustRequestDTO req = new CreditAdjustRequestDTO();
        req.setNewLimit(10); // below used 40
        req.setReason("reduce");

        service.adjustQueryCredits("u1", req, "admin");

        assertEquals(40, u.getQueryCreditsLimit()); // clamped to used
    }

    @Test
    void requireUserMissingThrowsNotFound() {
        when(userRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.consumeQuery("nope", new ConsumeQueryRequestDTO()));
    }
}
