package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminProfileDTO {
    private String userId;
    private String name;
    private String email;
    private String phone;
    private User.PlanTier planTier;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private Integer queryCreditsUsed;
    private Integer queryCreditsLimit;
    private List<String> enabledModules;
    private String referralCode;
    private String referredBy;
    private int completedLecturesCount;
    private int totalQuestionsAsked;
}
