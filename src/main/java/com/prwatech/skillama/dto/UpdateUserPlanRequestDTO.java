package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.User;
import lombok.Data;

@Data
public class UpdateUserPlanRequestDTO {
    /** FREEMIUM, PAID, or ENTERPRISE */
    private User.PlanTier planTier;
    /** Why plan was changed (admin audit) */
    private String reason;
}
