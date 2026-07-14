package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payment_transactions")
public class PaymentTransaction {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String planCode;
    private double amountInr;

    @Indexed
    private TransactionStatus status;

    private String provider;
    private String providerOrderId;
    private String providerPaymentId;
    private String providerSignature;

    private String subscriptionId;
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public enum TransactionStatus {
        PENDING, SUCCESS, FAILED
    }
}
