package com.prwatech.skillama.controller;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.CodeAssistRequestDTO;
import com.prwatech.skillama.dto.ProxiedAudioDTO;
import com.prwatech.skillama.service.CodeAssistService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Debug / Code Execution endpoints — logged-in learners only. The backend now mediates
 * the ai-tutor call (previously the browser hit ai-tutor directly), so it can persist
 * the interaction for admin content tracking, same as AI Mentor/AI Exam already do.
 */
@RestController
@RequestMapping("/skillama/code-assist")
@RequiredArgsConstructor
public class CodeAssistController {

    private final CodeAssistService codeAssistService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @PostMapping("/debug")
    public ResponseEntity<?> debug(@RequestBody CodeAssistRequestDTO request, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(codeAssistService.runDebug(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody CodeAssistRequestDTO request, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(codeAssistService.runCodeExecution(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Proxies an interaction's spoken-explanation audio bytes — the raw ai-tutor URL
     * never reaches the client, only the audio itself, after an ownership check.
     */
    @GetMapping("/interactions/{interactionId}/audio")
    public ResponseEntity<?> getInteractionAudio(
            @PathVariable String interactionId, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        try {
            ProxiedAudioDTO audio = codeAssistService.getInteractionAudio(userId, interactionId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(audio.getContentType()))
                    .body(audio.getData());
        } catch (NotFoundException e) {
            return ResponseEntity.notFound().build();
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
}
