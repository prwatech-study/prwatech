package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EventLearnerAccessDTO {
    private boolean active;
    private String eventName;
    private String limitMode;
    private Double creditsRemaining;
    private LocalDateTime endsAt;
}
