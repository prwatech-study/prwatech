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
@Document(collection = "upgrade_requests")
public class UpgradeRequest {
    @Id
    private String id;

    @Indexed
    private String userId;
    private String userName;
    private String userEmail;
    private String userPhone;

    private String source;
    private String courseId;
    private String courseName;
    private User.PlanTier planTier;
    private Integer queryCreditsUsed;
    private Integer queryCreditsLimit;
    private String message;

    @Indexed
    private RequestStatus status;
    private String notes;
    private String contactedByAdminId;
    private LocalDateTime contactedAt;

    @Indexed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum RequestStatus {
        NEW, CONTACTED, CLOSED
    }
}
