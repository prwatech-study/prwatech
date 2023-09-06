package com.prwatech.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RazorpayPayment {

    @JsonProperty(value = "id")
    private String paymentId;
    private Integer amount;
    private String status;
    private String orderId;
    private String method;
    private Boolean captured;
}
