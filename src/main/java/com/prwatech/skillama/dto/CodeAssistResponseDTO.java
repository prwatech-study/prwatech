package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Debug/Code Execution result returned to the client — the raw ai-tutor audio URL never appears here. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeAssistResponseDTO {
    private String interactionId;
    private String codeOutput;
    private String correctedCode;
    private String responseText;
    private Boolean hasAudio;
    private String subtitlePath;
}
