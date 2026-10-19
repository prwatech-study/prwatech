package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** One row per low-credit email, including failed sends an admin can resend. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "low_credit_emails")
public class LowCreditEmailLog {

    public enum Status {
        SENT,
        FAILED
    }

    @Id
    private String id;
    @Indexed
    private String userId;
    private String userName;
    private String email;
    private String creditsLeft;
    private Status status;
    private String error;
    @Builder.Default
    private int attemptCount = 1;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
