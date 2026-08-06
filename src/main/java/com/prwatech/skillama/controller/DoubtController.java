package com.prwatech.skillama.controller;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.dto.AskDoubtRequestDTO;
import com.prwatech.skillama.dto.DoubtFeedbackRequestDTO;
import com.prwatech.skillama.dto.DoubtFollowUpRequestDTO;
import com.prwatech.skillama.dto.DoubtResponseDTO;
import com.prwatech.skillama.dto.DoubtStatusUpdateRequestDTO;
import com.prwatech.skillama.dto.ProxiedAudioDTO;
import com.prwatech.skillama.service.DoubtService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * AI Mentor doubt endpoints — logged-in learners only (no guest/session mode,
 * unlike chat/track, since a doubt is always scoped to an enrolled course).
 */
@RestController
@RequestMapping("/skillama/ai-mentor")
@RequiredArgsConstructor
public class DoubtController {

    private final DoubtService doubtService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @PostMapping("/doubts")
    public ResponseEntity<?> askDoubt(
            @RequestBody AskDoubtRequestDTO request, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(doubtService.askDoubt(userId, request));
        } catch (AiBudgetLimitException e) {
            return budgetLimitResponse(e);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/doubts")
    public ResponseEntity<?> listMyDoubts(
            @RequestParam(required = false) String courseId, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        List<DoubtResponseDTO> doubts = doubtService.listMyDoubts(userId, courseId);
        return ResponseEntity.ok(doubts);
    }

    @GetMapping("/doubts/{doubtId}")
    public ResponseEntity<?> getDoubt(@PathVariable String doubtId, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(doubtService.getDoubt(userId, doubtId));
    }

    @PostMapping("/doubts/{doubtId}/follow-up")
    public ResponseEntity<?> addFollowUp(
            @PathVariable String doubtId,
            @RequestBody DoubtFollowUpRequestDTO request,
            HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(doubtService.addFollowUp(userId, doubtId, request));
        } catch (AiBudgetLimitException e) {
            return budgetLimitResponse(e);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/doubts/{doubtId}/feedback")
    public ResponseEntity<?> submitFeedback(
            @PathVariable String doubtId,
            @RequestBody DoubtFeedbackRequestDTO request,
            HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(doubtService.submitFeedback(userId, doubtId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PatchMapping("/doubts/{doubtId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String doubtId,
            @RequestBody DoubtStatusUpdateRequestDTO request,
            HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(
                    doubtService.updateStatus(userId, doubtId, request != null ? request.getStatus() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Proxies a message's spoken-answer audio bytes — the raw ai-tutor URL never reaches
     * the client, only the audio itself, after an ownership check.
     */
    @GetMapping("/doubts/{doubtId}/messages/{messageId}/audio")
    public ResponseEntity<?> getMessageAudio(
            @PathVariable String doubtId,
            @PathVariable String messageId,
            HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            ProxiedAudioDTO audio = doubtService.getMessageAudio(userId, doubtId, messageId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(audio.getContentType()))
                    .body(audio.getData());
        } catch (NotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private String resolveUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return skillamaAuthSupport.resolveUserIdFromRequest(request);
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Unauthorized"));
    }

    private ResponseEntity<Map<String, Object>> budgetLimitResponse(AiBudgetLimitException e) {
        return ResponseEntity.status(429).body(Map.of(
                "status", "error",
                "message", e.getMessage(),
                "aiBudgetLimitReached", true,
                "aiCostUsedUsd", e.getAiCostUsedUsd(),
                "aiCostLimitUsd", e.getAiCostLimitUsd()));
    }
}
