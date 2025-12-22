package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitGuestSessionRequestDTO {
    private String deviceFingerprint;  // Optional: for device tracking
    private String userAgent;
    private String ipAddress;          // Optional: for analytics
}

