package com.prwatech.skillama.service;

import com.prwatech.skillama.billing.PaymentProvider;
import com.prwatech.skillama.dto.AiBudgetDTO;
import com.prwatech.skillama.dto.BillingCheckoutRequestDTO;
import com.prwatech.skillama.dto.BillingCheckoutResponseDTO;
import com.prwatech.skillama.dto.BillingConfirmRequestDTO;
import com.prwatech.skillama.dto.SubscriptionPlanDTO;
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
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    public static final String PLAN_SPARK = "SPARK";
    public static final String PLAN_PULSE = "PULSE";
    public static final String PLAN_NOVA = "NOVA";
    public static final String PLAN_QUANTUM = "QUANTUM";

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final SkillamaUserRepository userRepository;
    private final PaymentProvider paymentProvider;
    private final AiUsageService aiUsageService;
    private final UsdInrExchangeRateService usdInrExchangeRateService;

    @PostConstruct
    public void seedPlansIfEmpty() {
        if (planRepository.count() > 0) {
            return;
        }
        List<String> freemiumModules = new ArrayList<>(FreemiumService.FREEMIUM_BASE_MODULES);
        List<String> premiumModules = new ArrayList<>(FreemiumService.PREMIUM_MODULES);

        planRepository.saveAll(Arrays.asList(
                SubscriptionPlan.builder()
                        .code(PLAN_SPARK)
                        .displayName("Spark")
                        .description("Free tier with a limited AI wallet.")
                        .priceInr(0)
                        .walletInr(null)
                        .enabledModules(freemiumModules)
                        .sortOrder(0)
                        .active(true)
                        .planTier(User.PlanTier.FREEMIUM)
                        .build(),
                SubscriptionPlan.builder()
                        .code(PLAN_PULSE)
                        .displayName("Pulse")
                        .description("Entry paid plan — pay ₹999, get ₹1,500 AI wallet each month.")
                        .priceInr(999)
                        .walletInr(1500.0)
                        .enabledModules(premiumModules)
                        .sortOrder(1)
                        .active(true)
                        .planTier(User.PlanTier.PAID)
                        .build(),
                SubscriptionPlan.builder()
                        .code(PLAN_NOVA)
                        .displayName("Nova")
                        .description("Mid tier — pay ₹4,999, get ₹7,500 AI wallet each month.")
                        .priceInr(4999)
                        .walletInr(7500.0)
                        .enabledModules(premiumModules)
                        .sortOrder(2)
                        .active(true)
                        .planTier(User.PlanTier.PAID)
                        .build(),
                SubscriptionPlan.builder()
                        .code(PLAN_QUANTUM)
                        .displayName("Quantum")
                        .description("Top tier — pay ₹9,999, get ₹15,000 AI wallet each month.")
                        .priceInr(9999)
                        .walletInr(15000.0)
                        .enabledModules(premiumModules)
                        .sortOrder(3)
                        .active(true)
                        .planTier(User.PlanTier.PAID)
                        .build()
        ));
    }

    public List<SubscriptionPlanDTO> listActivePlans() {
        seedPlansIfEmpty();
        return planRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toPlanDto)
                .collect(Collectors.toList());
    }

    public SubscriptionPlan requirePlan(String planCode) {
        seedPlansIfEmpty();
        String code = normalizePlanCode(planCode);
        return planRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + code));
    }

    public UserSubscriptionDTO getCurrentSubscription(String userId) {
        User user = requireUser(userId);
        UserSubscription sub = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null);

        String planCode = user.getSubscriptionPlanCode() != null
                ? user.getSubscriptionPlanCode()
                : (sub != null ? sub.getPlanCode() : PLAN_SPARK);
        SubscriptionPlan plan = planRepository.findByCode(normalizePlanCode(planCode)).orElse(null);

        return UserSubscriptionDTO.builder()
                .subscriptionId(sub != null ? sub.getId() : null)
                .planCode(planCode)
                .planDisplayName(plan != null ? plan.getDisplayName() : planCode)
                .status(user.getSubscriptionStatus() != null
                        ? user.getSubscriptionStatus()
                        : (sub != null ? sub.getStatus().name() : "ACTIVE"))
                .autoRenew(sub != null && sub.isAutoRenew())
                .currentPeriodStart(sub != null ? sub.getCurrentPeriodStart() : null)
                .currentPeriodEnd(user.getCurrentPeriodEnd() != null
                        ? user.getCurrentPeriodEnd()
                        : (sub != null ? sub.getCurrentPeriodEnd() : null))
                .planTier(user.getPlanTier())
                .aiBudget(aiUsageService.getAiBudget(user))
                .priceInr(plan != null ? plan.getPriceInr() : 0)
                .walletInr(plan != null ? plan.getWalletInr() : null)
                .provider(sub != null ? sub.getProvider() : null)
                .build();
    }

    @Transactional
    public BillingCheckoutResponseDTO checkout(String userId, BillingCheckoutRequestDTO request) {
        if (request == null || request.getPlanCode() == null || request.getPlanCode().isBlank()) {
            throw new IllegalArgumentException("planCode is required");
        }
        SubscriptionPlan plan = requirePlan(request.getPlanCode());
        if (PLAN_SPARK.equals(plan.getCode())) {
            throw new IllegalArgumentException("Spark is free — no checkout required");
        }
        requireUser(userId);

        BigDecimal amount = BigDecimal.valueOf(plan.getPriceInr()).setScale(2, RoundingMode.HALF_UP);
        PaymentProvider.PaymentOrder order = paymentProvider.createOrder(userId, plan.getCode(), amount);

        PaymentTransaction txn = PaymentTransaction.builder()
                .userId(userId)
                .planCode(plan.getCode())
                .amountInr(plan.getPriceInr())
                .status(PaymentTransaction.TransactionStatus.PENDING)
                .provider(order.provider())
                .providerOrderId(order.orderId())
                .createdAt(IndiaTime.now())
                .updatedAt(IndiaTime.now())
                .build();
        txn = transactionRepository.save(txn);

        return BillingCheckoutResponseDTO.builder()
                .orderId(order.orderId())
                .provider(order.provider())
                .amountInr(plan.getPriceInr())
                .currency(order.currency())
                .planCode(plan.getCode())
                .transactionId(txn.getId())
                .demoPayment("MOCK".equalsIgnoreCase(order.provider()))
                .build();
    }

    @Transactional
    public UserSubscriptionDTO confirm(String userId, BillingConfirmRequestDTO request) {
        if (request == null || request.getOrderId() == null || request.getOrderId().isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        PaymentTransaction txn = transactionRepository.findByProviderOrderId(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
        if (!userId.equals(txn.getUserId())) {
            throw new IllegalArgumentException("Order does not belong to this user");
        }
        if (txn.getStatus() == PaymentTransaction.TransactionStatus.SUCCESS) {
            return getCurrentSubscription(userId);
        }

        String planCode = request.getPlanCode() != null && !request.getPlanCode().isBlank()
                ? request.getPlanCode()
                : txn.getPlanCode();
        SubscriptionPlan plan = requirePlan(planCode);

        PaymentProvider.PaymentVerification verification = paymentProvider.verifyAndCapture(
                request.getOrderId(), request.getPaymentId(), request.getSignature());

        if (!verification.success()) {
            txn.setStatus(PaymentTransaction.TransactionStatus.FAILED);
            txn.setFailureReason(verification.failureReason());
            txn.setUpdatedAt(IndiaTime.now());
            transactionRepository.save(txn);
            throw new IllegalStateException(
                    verification.failureReason() != null ? verification.failureReason() : "Payment failed");
        }

        txn.setProviderPaymentId(verification.paymentId());
        txn.setProviderSignature(verification.signature());
        txn.setStatus(PaymentTransaction.TransactionStatus.SUCCESS);
        txn.setCompletedAt(IndiaTime.now());
        txn.setUpdatedAt(IndiaTime.now());

        User user = requireUser(userId);
        UserSubscription subscription = activate(user, plan, paymentProvider.providerName());
        txn.setSubscriptionId(subscription.getId());
        transactionRepository.save(txn);

        return getCurrentSubscription(userId);
    }

    /**
     * Activates or upgrades a subscription for the user. Used by confirm and future webhooks.
     */
    @Transactional
    public UserSubscription activate(User user, SubscriptionPlan plan, String provider) {
        LocalDateTime now = IndiaTime.now();
        LocalDateTime periodEnd = now.plusMonths(1);

        // Expire prior active subscriptions
        subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                        user.getId(), UserSubscription.SubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(UserSubscription.SubscriptionStatus.EXPIRED);
                    existing.setUpdatedAt(now);
                    subscriptionRepository.save(existing);
                });

        UserSubscription sub = UserSubscription.builder()
                .userId(user.getId())
                .planCode(plan.getCode())
                .status(UserSubscription.SubscriptionStatus.ACTIVE)
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd)
                .autoRenew(true)
                .provider(provider != null ? provider : "MOCK")
                .createdAt(now)
                .updatedAt(now)
                .build();
        sub = subscriptionRepository.save(sub);

        user.setSubscriptionPlanCode(plan.getCode());
        user.setSubscriptionStatus(UserSubscription.SubscriptionStatus.ACTIVE.name());
        user.setCurrentPeriodEnd(periodEnd);
        user.setPlanTier(plan.getPlanTier() != null ? plan.getPlanTier() : User.PlanTier.PAID);
        user.setEnabledModules(new ArrayList<>(
                plan.getEnabledModules() != null && !plan.getEnabledModules().isEmpty()
                        ? plan.getEnabledModules()
                        : FreemiumService.PREMIUM_MODULES));

        if (plan.getWalletInr() != null && plan.getWalletInr() > 0) {
            double rate = usdInrExchangeRateService.getUsdToInrRate();
            if (rate <= 0) {
                rate = 83.0;
            }
            double walletUsd = plan.getWalletInr() / rate;
            user.setAiWalletLimitUsd(roundUsd(walletUsd));
        } else {
            user.setAiWalletLimitUsd(null);
        }

        // Fresh wallet period on activate/renew
        user.setAiCostUsdThisPeriod(0.0);
        user.setAiCostPeriodStart(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        return sub;
    }

    @Transactional
    public UserSubscriptionDTO cancel(String userId) {
        User user = requireUser(userId);
        UserSubscription sub = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByUpdatedAtDesc(userId, UserSubscription.SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription to cancel"));

        LocalDateTime now = IndiaTime.now();
        sub.setAutoRenew(false);
        sub.setStatus(UserSubscription.SubscriptionStatus.CANCELLED);
        sub.setCancelledAt(now);
        sub.setUpdatedAt(now);
        subscriptionRepository.save(sub);

        user.setSubscriptionStatus(UserSubscription.SubscriptionStatus.CANCELLED.name());
        user.setUpdatedAt(now);
        // Keep plan entitlements until currentPeriodEnd; a future job can expire them.
        userRepository.save(user);

        return getCurrentSubscription(userId);
    }

    private SubscriptionPlanDTO toPlanDto(SubscriptionPlan plan) {
        // Wallet-derived: a plan is unmetered only for ENTERPRISE, or PAID with no wallet
        // allocation — mirroring AiUsageService.isUnlimitedForBudget at the plan level.
        boolean unlimitedQueries = plan.getPlanTier() == User.PlanTier.ENTERPRISE
                || (plan.getPlanTier() == User.PlanTier.PAID
                        && (plan.getWalletInr() == null || plan.getWalletInr() <= 0));
        return SubscriptionPlanDTO.builder()
                .code(plan.getCode())
                .displayName(plan.getDisplayName())
                .description(plan.getDescription())
                .priceInr(plan.getPriceInr())
                .walletInr(plan.getWalletInr())
                .unlimitedQueries(unlimitedQueries)
                .enabledModules(plan.getEnabledModules())
                .sortOrder(plan.getSortOrder())
                .planTier(plan.getPlanTier())
                .build();
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static String normalizePlanCode(String code) {
        return code == null ? PLAN_SPARK : code.trim().toUpperCase();
    }

    private static double roundUsd(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
