package com.prwatech.common.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AfterPaymentRequestPayload {
    @JsonProperty(value = "userId")
    private String userId;
    @JsonProperty(value = "courseId")
    private String courseId;
    @JsonProperty(value = "paymentId")
    private String paymentId;
}
