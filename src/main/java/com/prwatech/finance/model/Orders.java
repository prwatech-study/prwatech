package com.prwatech.finance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document("order")
public class Orders {

    @Id
    private String id;

    @Field("order_id")
    private String orderId;

    @Field("entity")
    private String entity;

    @Field("amount")
    private Integer amount;

    @Field("amount_paid")
    private Integer amountPaid;

    @Field("amount_due")
    private Integer amountDue;

    @Field("currency")
    private String currency;

    @Field("receipt")
    private String receipt;

    @Field("status")
    private String status;

    @Field("attempts")
    private Integer attempts;

    @Field("notes")
    private String[] notes;

    @Field("creation_time_stamp")
    private Date creationTime;

    @Field("created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Field("updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Field("payment_id")
    private String paymentId;
}
