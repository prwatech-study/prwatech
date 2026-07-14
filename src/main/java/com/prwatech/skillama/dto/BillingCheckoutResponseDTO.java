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
    private String currency;
    private String planCode;
    private String transactionId;
    /** Hint for UI when using mock gateway. */
    private Boolean demoPayment;
}
