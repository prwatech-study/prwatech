package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.AiAnswerFeedbackRequestDTO;
import com.prwatech.skillama.model.AiAnswerFeedback;
import com.prwatech.skillama.service.AiAnswerFeedbackService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/skillama/user-profile/ai-feedback")
@RequiredArgsConstructor
public class AiAnswerFeedbackController {

    private final AiAnswerFeedbackService aiAnswerFeedbackService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @PostMapping
    public ResponseEntity<?> submitFeedback(
            @RequestBody AiAnswerFeedbackRequestDTO request, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }
        try {
            AiAnswerFeedback saved = aiAnswerFeedbackService.submit(userId, request);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "messageId", saved.getMessageId(),
                    "helpful", saved.isHelpful()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
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
}
