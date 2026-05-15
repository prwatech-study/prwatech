package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.prwatech.skillama.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccessControlResponseDTO {
    private String userId;                    // null for guests
    private String sessionId;
    private Boolean isGuest;
    private String courseId;
    private String courseName;
    private User.PlanTier planTier;

    private List<ModuleAccessDTO> modules;
    private FeatureAccessDTO features;
    private ProgressSummaryDTO progress;
    private QueryCreditsDTO queryCredits;
}

