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
@Document(collection = "credit_adjustment_logs")
public class CreditAdjustmentLog {
    @Id
    private String id;

    @Indexed
    private String userId;
    private String userEmail;

    @Indexed
    private String adminId;
    private String adminEmail;

    private int delta;
    private int balanceBeforeUsed;
    private int balanceAfterUsed;
    private Integer limitBefore;
    private Integer limitAfter;
    private String reason;

    @Indexed
    private LocalDateTime createdAt;
}
