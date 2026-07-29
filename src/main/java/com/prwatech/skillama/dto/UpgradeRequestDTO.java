package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.UpgradeRequest;
import com.prwatech.skillama.model.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UpgradeRequestDTO {
    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private String source;
    private String courseId;
    private String courseName;
    private User.PlanTier planTier;
    private String message;
    private UpgradeRequest.RequestStatus status;
    private String notes;
    private String contactedByAdminId;
    private LocalDateTime contactedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
