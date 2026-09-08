package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.OrgAuthGoogleRequestDTO;
import com.prwatech.skillama.dto.OrgAuthLoginRequestDTO;
import com.prwatech.skillama.dto.OrgAuthMicrosoftRequestDTO;
import com.prwatech.skillama.service.OrgAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/skillama/api/org/auth")
@RequiredArgsConstructor
public class OrgAuthController {

    private final OrgAuthService orgAuthService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody OrgAuthLoginRequestDTO request) {
        try {
            Map<String, Object> result = orgAuthService.login(request);
            if ("conflict".equals(result.get("status"))) {
                return ResponseEntity.status(409).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> google(@RequestBody OrgAuthGoogleRequestDTO request) {
        try {
            Map<String, Object> result = orgAuthService.loginWithGoogle(request);
            if ("conflict".equals(result.get("status"))) {
                return ResponseEntity.status(409).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/microsoft")
    public ResponseEntity<Map<String, Object>> microsoft(@RequestBody OrgAuthMicrosoftRequestDTO request) {
        try {
            Map<String, Object> result = orgAuthService.loginWithMicrosoft(request);
            if ("conflict".equals(result.get("status"))) {
                return ResponseEntity.status(409).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
