package com.prwatech.skillama.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Always-succeed payment provider for dummy checkout until a real gateway is wired.
 */
@Component
@ConditionalOnProperty(name = "skillama.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String providerName() {
        return "MOCK";
    }

    @Override
    public PaymentOrder createOrder(String userId, String planCode, BigDecimal amountInr) {
        String orderId = "mock_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new PaymentOrder(orderId, providerName(), amountInr, "INR");
    }

    @Override
    public PaymentVerification verifyAndCapture(String orderId, String paymentId, String signature) {
        String resolvedPaymentId = (paymentId != null && !paymentId.isBlank())
                ? paymentId
                : "mock_pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String resolvedSig = (signature != null && !signature.isBlank())
                ? signature
                : "mock_sig_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new PaymentVerification(true, orderId, resolvedPaymentId, resolvedSig, null);
    }
}
