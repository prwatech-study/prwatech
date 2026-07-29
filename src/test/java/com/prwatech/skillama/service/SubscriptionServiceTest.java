package com.prwatech.skillama.service;

import com.prwatech.skillama.billing.PaymentProvider;
import com.prwatech.skillama.dto.AiBudgetDTO;
import com.prwatech.skillama.dto.BillingCheckoutRequestDTO;
import com.prwatech.skillama.dto.BillingCheckoutResponseDTO;
import com.prwatech.skillama.dto.BillingConfirmRequestDTO;
import com.prwatech.skillama.dto.UserSubscriptionDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.PaymentTransaction;
import com.prwatech.skillama.model.SubscriptionPlan;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserSubscription;
import com.prwatech.skillama.repository.PaymentTransactionRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.SubscriptionPlanRepository;
import com.prwatech.skillama.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceTest {

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private UserSubscriptionRepository subscriptionRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private AiUsageService aiUsageService;
    @Mock private UsdInrExchangeRateService usdInrExchangeRateService;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(planRepository, subscriptionRepository,
                transactionRepository, userRepository, paymentProvider,
                aiUsageService, usdInrExchangeRateService);
        // Non-empty so seedPlansIfEmpty() is a no-op in every test.
        when(planRepository.count()).thenReturn(4L);
    }

    private SubscriptionPlan pulsePlan() {
        return SubscriptionPlan.builder()
                .code("PULSE").displayName("Pulse").priceInr(999).walletInr(1500.0)
                .enabledModules(List.of("ai-tutor"))
                .sortOrder(1).active(true).planTier(User.PlanTier.PAID)
                .build();
    }

    private SubscriptionPlan sparkPlan() {
        return SubscriptionPlan.builder()
                .code("SPARK").displayName("Spark").priceInr(0).walletInr(null)
                .enabledModules(List.of("basics"))
                .sortOrder(0).active(true).planTier(User.PlanTier.FREEMIUM)
                .build();
    }

    private User userWithId(String id) {
        return User.builder().id(id).email("u@x.com").build();
    }

    // ---------- checkout ----------

    @Test
    void checkoutRejectsMissingPlanCode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout("u1", new BillingCheckoutRequestDTO(null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout("u1", new BillingCheckoutRequestDTO("   ")));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout("u1", null));
    }

    @Test
    void checkoutRejectsFreeSparkPlan() {
        when(planRepository.findByCode("SPARK")).thenReturn(Optional.of(sparkPlan()));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.checkout("u1", new BillingCheckoutRequestDTO("spark")));
        assertTrue(ex.getMessage().toLowerCase().contains("free"));
    }

    @Test
    void checkoutUnknownPlanThrowsNotFound() {
        when(planRepository.findByCode("GHOST")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.checkout("u1", new BillingCheckoutRequestDTO("ghost")));
    }

    @Test
    void checkoutCreatesPendingTransactionAndReturnsOrder() {
        when(planRepository.findByCode("PULSE")).thenReturn(Optional.of(pulsePlan()));
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithId("u1")));
        when(paymentProvider.createOrder(eq("u1"), eq("PULSE"), any(BigDecimal.class)))
                .thenReturn(new PaymentProvider.PaymentOrder("ord_1", "MOCK", BigDecimal.valueOf(999), "INR"));
        when(transactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(inv -> {
                    PaymentTransaction t = inv.getArgument(0);
                    t.setId("txn_1");
                    return t;
                });

        BillingCheckoutResponseDTO res = service.checkout("u1", new BillingCheckoutRequestDTO("pulse"));

        assertEquals("ord_1", res.getOrderId());
        assertEquals("MOCK", res.getProvider());
        assertEquals(999.0, res.getAmountInr());
        assertEquals("INR", res.getCurrency());
        assertEquals("PULSE", res.getPlanCode());
        assertEquals("txn_1", res.getTransactionId());
        assertTrue(res.getDemoPayment());

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(PaymentTransaction.TransactionStatus.PENDING, captor.getValue().getStatus());
        assertEquals("ord_1", captor.getValue().getProviderOrderId());
    }

    // ---------- confirm ----------

    @Test
    void confirmRejectsMissingOrderId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.confirm("u1", BillingConfirmRequestDTO.builder().orderId(" ").build()));
    }

    @Test
    void confirmUnknownOrderThrowsNotFound() {
        when(transactionRepository.findByProviderOrderId("ord_x")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.confirm("u1", BillingConfirmRequestDTO.builder().orderId("ord_x").build()));
    }

    @Test
    void confirmRejectsOrderBelongingToAnotherUser() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id("t1").userId("owner").providerOrderId("ord_1").planCode("PULSE")
                .status(PaymentTransaction.TransactionStatus.PENDING).build();
        when(transactionRepository.findByProviderOrderId("ord_1")).thenReturn(Optional.of(txn));

        assertThrows(IllegalArgumentException.class,
                () -> service.confirm("intruder", BillingConfirmRequestDTO.builder().orderId("ord_1").build()));
    }

    @Test
    void confirmFailedVerificationMarksTransactionFailedAndThrows() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id("t1").userId("u1").providerOrderId("ord_1").planCode("PULSE")
                .status(PaymentTransaction.TransactionStatus.PENDING).build();
        when(transactionRepository.findByProviderOrderId("ord_1")).thenReturn(Optional.of(txn));
        when(planRepository.findByCode("PULSE")).thenReturn(Optional.of(pulsePlan()));
        when(paymentProvider.verifyAndCapture(eq("ord_1"), any(), any()))
                .thenReturn(new PaymentProvider.PaymentVerification(false, "ord_1", null, null, "card declined"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.confirm("u1", BillingConfirmRequestDTO.builder()
                        .orderId("ord_1").paymentId("p").signature("s").build()));
        assertTrue(ex.getMessage().contains("card declined"));

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(PaymentTransaction.TransactionStatus.FAILED, captor.getValue().getStatus());
        assertEquals("card declined", captor.getValue().getFailureReason());
        // No subscription activated on failure.
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmAlreadySuccessfulIsIdempotent() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id("t1").userId("u1").providerOrderId("ord_1").planCode("PULSE")
                .status(PaymentTransaction.TransactionStatus.SUCCESS).build();
        when(transactionRepository.findByProviderOrderId("ord_1")).thenReturn(Optional.of(txn));
        User user = userWithId("u1");
        user.setSubscriptionPlanCode("PULSE"); // already-paid state reflected by getCurrentSubscription
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                anyString(), any())).thenReturn(Optional.empty());
        when(planRepository.findByCode(anyString())).thenReturn(Optional.of(pulsePlan()));
        when(aiUsageService.getAiBudget(any())).thenReturn(AiBudgetDTO.builder().build());

        UserSubscriptionDTO dto = service.confirm("u1", BillingConfirmRequestDTO.builder().orderId("ord_1").build());

        assertEquals("PULSE", dto.getPlanCode());
        // Idempotent: no verification, no re-save of the txn.
        verify(paymentProvider, never()).verifyAndCapture(any(), any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmSuccessActivatesSubscriptionAndGrantsEntitlements() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id("t1").userId("u1").providerOrderId("ord_1").planCode("PULSE")
                .status(PaymentTransaction.TransactionStatus.PENDING).build();
        when(transactionRepository.findByProviderOrderId("ord_1")).thenReturn(Optional.of(txn));
        when(planRepository.findByCode("PULSE")).thenReturn(Optional.of(pulsePlan()));
        when(paymentProvider.verifyAndCapture(eq("ord_1"), any(), any()))
                .thenReturn(new PaymentProvider.PaymentVerification(true, "ord_1", "pay_1", "sig_1", null));
        when(paymentProvider.providerName()).thenReturn("MOCK");
        User user = userWithId("u1");
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(UserSubscription.class)))
                .thenAnswer(inv -> {
                    UserSubscription s = inv.getArgument(0);
                    s.setId("sub_1");
                    return s;
                });
        when(usdInrExchangeRateService.getUsdToInrRate()).thenReturn(80.0);
        when(aiUsageService.getAiBudget(any())).thenReturn(AiBudgetDTO.builder().build());

        UserSubscriptionDTO dto = service.confirm("u1", BillingConfirmRequestDTO.builder()
                .orderId("ord_1").paymentId("pay_1").signature("sig_1").build());

        assertEquals("PULSE", dto.getPlanCode());

        // User upgraded to PAID with premium entitlements and a fresh wallet.
        assertEquals("PULSE", user.getSubscriptionPlanCode());
        assertEquals(User.PlanTier.PAID, user.getPlanTier());
        assertEquals(UserSubscription.SubscriptionStatus.ACTIVE.name(), user.getSubscriptionStatus());
        // wallet 1500 INR / 80 = 18.75 USD
        assertEquals(18.75, user.getAiWalletLimitUsd());
        assertEquals(0.0, user.getAiCostUsdThisPeriod());

        // Transaction captured as SUCCESS and linked to subscription.
        assertEquals(PaymentTransaction.TransactionStatus.SUCCESS, txn.getStatus());
        assertEquals("sub_1", txn.getSubscriptionId());
    }

    @Test
    void activateExpiresPriorActiveSubscription() {
        User user = userWithId("u1");
        UserSubscription prior = UserSubscription.builder()
                .id("old").userId("u1").planCode("PULSE")
                .status(UserSubscription.SubscriptionStatus.ACTIVE).build();
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                eq("u1"), eq(UserSubscription.SubscriptionStatus.ACTIVE)))
                .thenReturn(Optional.of(prior));
        when(subscriptionRepository.save(any(UserSubscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usdInrExchangeRateService.getUsdToInrRate()).thenReturn(83.0);

        service.activate(user, pulsePlan(), "MOCK");

        assertEquals(UserSubscription.SubscriptionStatus.EXPIRED, prior.getStatus());
    }

    @Test
    void activateFallsBackToDefaultRateWhenApiReturnsNonPositive() {
        User user = userWithId("u1");
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(UserSubscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usdInrExchangeRateService.getUsdToInrRate()).thenReturn(0.0);

        service.activate(user, pulsePlan(), "MOCK");

        // 1500 / 83.0 fallback
        assertEquals(Math.round((1500.0 / 83.0) * 1_000_000.0) / 1_000_000.0, user.getAiWalletLimitUsd());
    }

    // ---------- cancel ----------

    @Test
    void cancelWithoutActiveSubscriptionThrowsNotFound() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithId("u1")));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                eq("u1"), eq(UserSubscription.SubscriptionStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.cancel("u1"));
    }

    @Test
    void cancelMarksSubscriptionCancelledAndDisablesAutoRenew() {
        User user = userWithId("u1");
        UserSubscription sub = UserSubscription.builder()
                .id("sub_1").userId("u1").planCode("PULSE").autoRenew(true)
                .status(UserSubscription.SubscriptionStatus.ACTIVE).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(anyString(), any()))
                .thenReturn(Optional.of(sub));
        when(planRepository.findByCode(anyString())).thenReturn(Optional.of(pulsePlan()));
        when(aiUsageService.getAiBudget(any())).thenReturn(AiBudgetDTO.builder().build());

        service.cancel("u1");

        assertEquals(UserSubscription.SubscriptionStatus.CANCELLED, sub.getStatus());
        assertTrue(!sub.isAutoRenew());
        assertEquals(UserSubscription.SubscriptionStatus.CANCELLED.name(), user.getSubscriptionStatus());
    }

    // ---------- getCurrentSubscription ----------

    @Test
    void getCurrentSubscriptionDefaultsToSparkWhenNoPlanOrSubscription() {
        User user = userWithId("u1"); // no plan code set
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(anyString(), any()))
                .thenReturn(Optional.empty());
        when(planRepository.findByCode("SPARK")).thenReturn(Optional.of(sparkPlan()));
        when(aiUsageService.getAiBudget(any())).thenReturn(AiBudgetDTO.builder().build());

        UserSubscriptionDTO dto = service.getCurrentSubscription("u1");

        assertEquals("SPARK", dto.getPlanCode());
        assertEquals("Spark", dto.getPlanDisplayName());
    }

    @Test
    void requireUserMissingThrowsNotFound() {
        when(userRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getCurrentSubscription("nope"));
    }
}
