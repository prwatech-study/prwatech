package com.prwatech.common.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderDto implements Serializable {

    @JsonProperty("amount")
    private Integer amount;

    @JsonProperty("currency")
    private String currency="INR";

    @JsonProperty("receipt")
    private String receipt;

    @JsonProperty("notes")
    private Map<String, String> notes;

    @JsonProperty("partial_payment")
    private Boolean partialPayment=Boolean.FALSE;

    @JsonProperty("course_ids")
    private List<String> courseIds;
}
