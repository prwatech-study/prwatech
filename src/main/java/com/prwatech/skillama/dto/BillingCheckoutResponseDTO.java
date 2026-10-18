package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillingCheckoutResponseDTO {
    private String orderId;
    private String provider;
    private Double amountInr;
    /** Amount in paise for Razorpay Checkout.js (amountInr × 100). */
    private Long amountPaise;
    private String currency;
    private String planCode;
    private String transactionId;
    /** Hint for UI when using mock gateway. */
    private Boolean demoPayment;
    /**
     * Razorpay Key ID for Checkout.js only. Never the key secret.
     * Prefer this over a frontend env var so the secret cannot leak via NEXT_PUBLIC_*.
     */
    private String razorpayKeyId;
}
