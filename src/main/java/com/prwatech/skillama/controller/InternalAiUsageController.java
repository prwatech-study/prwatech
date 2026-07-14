package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.model.AiUsageEvent;
import com.prwatech.skillama.service.AiUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Service-to-service ingest from ai-tutor (no admin JWT). */
@RestController
@RequestMapping("/skillama/internal/ai-usage")
@RequiredArgsConstructor
public class InternalAiUsageController {

    private final AiUsageService aiUsageService;

    @PostMapping("/record")
    public ResponseEntity<Map<String, Object>> record(
            @RequestBody AiUsageRecordRequestDTO body,
            @RequestHeader(value = "X-AI-Usage-Key", required = false) String apiKey) {
        if (!aiUsageService.isValidInternalApiKey(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }
        try {
            AiUsageEvent event = aiUsageService.recordUsage(body);
            return ResponseEntity.ok(Map.of("status", "ok", "recorded", event != null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
