package com.prwatech.skillama.controller;

import com.prwatech.common.exception.ForbiddenException;
import com.prwatech.skillama.dto.PracticalExecutionRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.service.PracticalDatasetService;
import com.prwatech.skillama.service.PracticalExecutionService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import java.io.IOException;
import java.util.Map;

/**
 * Learner-facing practical-dataset endpoints — logged-in users only, same auth pattern as
 * CodeAssistController. Every response is proxied through this service; the S3 storage key is
 * never present in a response or a redirect, only through {@code PracticalDatasetService}.
 */
@RestController
@RequestMapping("/skillama/practical-datasets")
@RequiredArgsConstructor
public class PracticalDatasetController {

    private final PracticalDatasetService datasetService;
    private final PracticalExecutionService executionService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping("/{datasetId}")
    public ResponseEntity<?> get(@PathVariable String datasetId, HttpServletRequest request) {
        String userId = resolveUserId(request);
        if (userId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(datasetService.getSummary(userId, datasetId));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/{datasetId}/download")
    public ResponseEntity<?> download(@PathVariable String datasetId, HttpServletRequest request) {
        String userId = resolveUserId(request);
        if (userId == null) {
            return unauthorized();
        }
        try {
            PracticalDatasetService.Content content = datasetService.download(userId, datasetId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(content.fileName()).build());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(content.bytes());
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Failed to fetch dataset"));
        }
    }

    @PostMapping("/{datasetId}/execute")
    public ResponseEntity<?> execute(
            @PathVariable String datasetId,
            @RequestBody PracticalExecutionRequestDTO request,
            HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return unauthorized();
        }
        if (request == null || request.getTask() == null || request.getTask().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "task is required"));
        }
        try {
            return ResponseEntity.ok(executionService.execute(userId, datasetId, request.getTask()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("status", "error", "message", e.getMessage()));
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
