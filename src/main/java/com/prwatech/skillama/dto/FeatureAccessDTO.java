package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureAccessDTO {
    private ChatFeatureDTO chat;
    private CodeExecutionFeatureDTO codeExecution;
    private DebugFeatureDTO debug;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatFeatureDTO {
        private Boolean accessible;
        private Integer questionsRemaining;
        private Boolean limitReached;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeExecutionFeatureDTO {
        private Boolean accessible;
        private String reason;           // "Login required" if locked
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugFeatureDTO {
        private Boolean accessible;
        private String reason;
    }
}

