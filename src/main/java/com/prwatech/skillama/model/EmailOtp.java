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
@Document(collection = "email_otps")
public class EmailOtp {
    @Id
    private String id;

    @Indexed
    private String email;

    private String otpHash;
    private OtpPurpose purpose;
    private String verificationToken;

    @Indexed
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    public enum OtpPurpose {
        SIGNUP, LOGIN, MIGRATE_FREEMIUM
    }
}
