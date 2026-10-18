package com.prwatech.skillama.billing;

import com.prwatech.common.configuration.AppContext;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Razorpay Standard Checkout: create Orders API orders and verify payment signatures.
 * Key secret stays server-side via {@link AppContext} / env vars — never exposed to the LMS.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "skillama.payment.provider", havingValue = "razorpay")
public class RazorpayPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentProvider.class);
    private static final int MIN_AMOUNT_PAISE = 100;
    private static final int RECEIPT_MAX_LEN = 40;

    private final AppContext appContext;

    @Override
    public String providerName() {
        return "RAZORPAY";
    }

    @Override
    public PaymentOrder createOrder(String userId, String planCode, BigDecimal amountInr) {
        String keyId = requireKeyId();
        String secret = requireSecret();

        if (amountInr == null || amountInr.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        long amountPaise = amountInr
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        if (amountPaise < MIN_AMOUNT_PAISE) {
            throw new IllegalArgumentException("amount must be at least 100 paise (₹1)");
        }

        String receipt = buildReceipt(userId, planCode);

        try {
            RazorpayClient client = new RazorpayClient(keyId, secret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", receipt);
            JSONObject notes = new JSONObject();
            notes.put("userId", userId != null ? userId : "");
            notes.put("planCode", planCode != null ? planCode : "");
            orderRequest.put("notes", notes);

            Order order = client.orders.create(orderRequest);
            String orderId = order.get("id");
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalStateException("Razorpay returned an empty order id");
            }
            return new PaymentOrder(orderId, providerName(), amountInr, "INR", keyId);
        } catch (RazorpayException e) {
            log.error("Razorpay create order failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Razorpay order creation failed";
            if (msg.toLowerCase().contains("auth") || msg.contains("401")) {
                throw new IllegalStateException("Razorpay authentication failed — check API keys", e);
            }
            throw new IllegalStateException("Razorpay order creation failed: " + msg, e);
        }
    }

    @Override
    public PaymentVerification verifyAndCapture(String orderId, String paymentId, String signature) {
        if (orderId == null || orderId.isBlank()
                || paymentId == null || paymentId.isBlank()
                || signature == null || signature.isBlank()) {
            return new PaymentVerification(
                    false, orderId, paymentId, signature, "Missing payment verification fields");
        }

        try {
            String secret = requireSecret();
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);

            boolean valid = Utils.verifyPaymentSignature(options, secret);
            if (!valid) {
                return new PaymentVerification(
                        false, orderId, paymentId, signature, "Invalid payment signature");
            }
            return new PaymentVerification(true, orderId, paymentId, signature, null);
        } catch (RazorpayException e) {
            log.warn("Razorpay signature verification error: {}", e.getMessage());
            return new PaymentVerification(
                    false, orderId, paymentId, signature, "Invalid payment signature");
        } catch (IllegalStateException e) {
            return new PaymentVerification(
                    false, orderId, paymentId, signature, e.getMessage());
        }
    }

    private String requireKeyId() {
        String key = appContext.getRazorpayKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Razorpay key id missing — set PRWATECH_RZ_KEY or RAZORPAY_KEY_ID");
        }
        return key.trim();
    }

    private String requireSecret() {
        String secret = appContext.getRazorpaySecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Razorpay key secret missing — set PRWATECH_RZ_SECRET or RAZORPAY_KEY_SECRET");
        }
        return secret.trim();
    }

    static String buildReceipt(String userId, String planCode) {
        String raw = "sk_"
                + (planCode != null ? planCode : "plan")
                + "_"
                + (userId != null ? userId : "user")
                + "_"
                + System.currentTimeMillis();
        if (raw.length() <= RECEIPT_MAX_LEN) {
            return raw;
        }
        return raw.substring(0, RECEIPT_MAX_LEN);
    }
}
