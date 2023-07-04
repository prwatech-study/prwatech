package com.prwatech.common.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RazorpayOrder {

    @JsonProperty("id")
    private String orderId;

    @JsonProperty("entity")
    private String entity;

    @JsonProperty("amount")
    private Integer amount;

    @JsonProperty("amount_paid")
    private Integer amountPaid;

    @JsonProperty("amount_due")
    private Integer amountDue;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("receipt")
    private String receipt;

    @JsonProperty("status")
    private String status;

    @JsonProperty("attempts")
    private Integer attempts;

    @JsonProperty("notes")
    private String[] notes;

    @JsonProperty("created_at")
    private Timestamp createdAt;

}
