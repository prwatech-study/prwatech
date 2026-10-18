package com.prwatech.skillama.billing;

import java.math.BigDecimal;

/**
 * Gateway abstraction so Razorpay/Stripe SDK or API can replace the mock later.
 */
public interface PaymentProvider {

    String providerName();

    PaymentOrder createOrder(String userId, String planCode, BigDecimal amountInr);

    PaymentVerification verifyAndCapture(String orderId, String paymentId, String signature);

    /**
     * @param publicKeyId Razorpay Key ID for Checkout.js (null for mock). Never include the secret.
     */
    record PaymentOrder(
            String orderId,
            String provider,
            BigDecimal amountInr,
            String currency,
            String publicKeyId) {
        public PaymentOrder(String orderId, String provider, BigDecimal amountInr, String currency) {
            this(orderId, provider, amountInr, currency, null);
        }
    }

    record PaymentVerification(
            boolean success,
            String orderId,
            String paymentId,
            String signature,
            String failureReason) {}
}
