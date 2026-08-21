package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.model.AiUsageEvent;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.service.AiUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Service-to-service ingest from ai-tutor (no admin JWT). */
@RestController
@RequestMapping("/skillama/internal/ai-usage")
@RequiredArgsConstructor
public class InternalAiUsageController {

    private final AiUsageService aiUsageService;
    private final SkillamaUserRepository userRepository;

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

    /**
     * Pre-flight budget gate for AI calls that reach ai-tutor directly (voice chat),
     * bypassing SkillamaAiClient.meteredCall. Applies the same central rule as every
     * other AI path — time wallet first, then credits (assertWithinBudget).
     * Unknown/blank userId is allowed: guests are governed by their own caps.
     */
    @GetMapping("/budget-check")
    public ResponseEntity<Map<String, Object>> budgetCheck(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "X-AI-Usage-Key", required = false) String apiKey) {
        if (!aiUsageService.isValidInternalApiKey(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(Map.of("allowed", true));
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(Map.of("allowed", true));
        }
        try {
            aiUsageService.assertWithinBudget(user);
            return ResponseEntity.ok(Map.of("allowed", true));
        } catch (AiBudgetLimitException e) {
            return ResponseEntity.ok(Map.of(
                    "allowed", false,
                    "message", e.getMessage(),
                    "aiBudgetLimitReached", true));
        }
    }
}
