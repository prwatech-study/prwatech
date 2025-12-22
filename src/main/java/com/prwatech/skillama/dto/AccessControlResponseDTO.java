package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessControlResponseDTO {
    private String userId;                    // null for guests
    private String sessionId;
    private Boolean isGuest;
    private String courseId;
    private String courseName;
    
    private List<ModuleAccessDTO> modules;
    private FeatureAccessDTO features;
    private ProgressSummaryDTO progress;
}

