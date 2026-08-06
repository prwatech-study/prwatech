package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.CodeAssistFeature;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Admin view of a single Debug/Code Execution interaction. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCodeAssistInteractionDTO {
    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private String courseId;
    private String courseName;
    private CodeAssistFeature feature;
    private String code;
    private String codeOutput;
    private String correctedCode;
    private String responseText;
    private Boolean hasAudio;
    private Boolean sandboxVerified;
    private LocalDateTime createdAt;
}
