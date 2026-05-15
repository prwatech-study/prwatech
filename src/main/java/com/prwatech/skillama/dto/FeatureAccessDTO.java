package com.prwatech.skillama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureAccessDTO {
    private ChatFeatureDTO chat;
    private CodeExecutionFeatureDTO codeExecution;
    private DebugFeatureDTO debug;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatFeatureDTO {
        private Boolean enabled;
        private Boolean accessible;
        private Integer questionsRemaining;
        private Boolean limitReached;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeExecutionFeatureDTO {
        private Boolean enabled;
        private Boolean accessible;
        private String reason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugFeatureDTO {
        private Boolean enabled;
        private Boolean accessible;
        private String reason;
    }
}

