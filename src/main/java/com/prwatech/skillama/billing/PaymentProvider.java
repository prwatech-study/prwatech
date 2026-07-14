package com.prwatech.skillama.billing;

import java.math.BigDecimal;

/**
 * Gateway abstraction so Razorpay/Stripe SDK or API can replace the mock later.
 */
public interface PaymentProvider {

    String providerName();

    PaymentOrder createOrder(String userId, String planCode, BigDecimal amountInr);

    PaymentVerification verifyAndCapture(String orderId, String paymentId, String signature);

    record PaymentOrder(String orderId, String provider, BigDecimal amountInr, String currency) {}

    record PaymentVerification(
            boolean success,
            String orderId,
            String paymentId,
            String signature,
            String failureReason) {}
}
