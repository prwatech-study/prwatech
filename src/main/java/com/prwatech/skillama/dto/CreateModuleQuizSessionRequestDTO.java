package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Starts a module quiz. Questions are generated server-side (backend calls the
 * AI service directly) — the client never supplies questions or an answer key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateModuleQuizSessionRequestDTO {
    private String courseId;
    private String moduleName;
    private List<String> topics;
}
