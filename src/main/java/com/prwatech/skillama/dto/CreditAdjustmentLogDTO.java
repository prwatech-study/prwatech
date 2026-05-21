package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CreditAdjustmentLogDTO {
    private String id;
    private String userId;
    private String userEmail;
    private String adminId;
    private String adminEmail;
    private int delta;
    private int balanceBeforeUsed;
    private int balanceAfterUsed;
    private Integer limitBefore;
    private Integer limitAfter;
    private String reason;
    private LocalDateTime createdAt;
}
