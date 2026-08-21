package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.TimeWalletConsumeRequestDTO;
import com.prwatech.skillama.dto.TimeWalletDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TimeWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/** Learner-facing view of their own time-based wallet (B2B seats). */
@RestController
@RequestMapping("/skillama/user-profile/time-wallet")
@RequiredArgsConstructor
public class TimeWalletController {

    private final TimeWalletService timeWalletService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping
    public ResponseEntity<?> getMyTimeWallet(HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }
        try {
            TimeWalletDTO status = timeWalletService.getStatus(userId);
            return ResponseEntity.ok(Map.of("status", 200, "data", status));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "User not found"));
        }
    }

    /**
     * Active-time heartbeat from paid feature surfaces (~every 30s while the tab is
     * visible and the learner is active). Server-side clamping lives in
     * TimeWalletService.consumeActiveTime. Returns the updated wallet so the header
     * pill can refresh from the response. No-op (active=false) for credit users.
     */
    @PostMapping("/consume")
    public ResponseEntity<?> consumeActiveTime(
            @RequestBody TimeWalletConsumeRequestDTO body, HttpServletRequest httpRequest) {
        String userId = resolveUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }
        try {
            TimeWalletDTO status = timeWalletService.consumeActiveTime(
                    userId,
                    body != null ? body.getSeconds() : null,
                    body != null ? body.getModule() : null);
            return ResponseEntity.ok(Map.of("status", 200, "data", status));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "User not found"));
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
