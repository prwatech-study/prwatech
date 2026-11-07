package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EventStudentDTO {
    private String userId;
    private String name;
    private String email;
    private LocalDateTime joinedAt;
    private double creditsBurned;
    private String status;
    private String revertReason;
}
