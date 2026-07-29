package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;
import lombok.Data;

@Data
public class UpgradeInterestRequestDTO {
    private String source;
    private String courseId;
    private String courseName;
    private User.PlanTier planTier;
    private String message;
}
