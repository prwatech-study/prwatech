package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReferralShareConfigDTO {
    private String title;
    private String shareMessage;
    private LocalDateTime updatedAt;
}
