package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.BillingCheckoutRequestDTO;
import com.prwatech.skillama.dto.BillingCheckoutResponseDTO;
import com.prwatech.skillama.dto.BillingConfirmRequestDTO;
import com.prwatech.skillama.dto.SubscriptionPlanDTO;
import com.prwatech.skillama.dto.UserSubscriptionDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.SkillamaAuthException;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/skillama/billing")
@RequiredArgsConstructor
public class BillingController {

    private final SubscriptionService subscriptionService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDTO>> listPlans() {
        return ResponseEntity.ok(subscriptionService.listActivePlans());
    }

    @GetMapping("/subscription")
    public ResponseEntity<UserSubscriptionDTO> getSubscription(HttpServletRequest request) {
        String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription(userId));
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(
            @RequestBody BillingCheckoutRequestDTO body,
            HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            BillingCheckoutResponseDTO response = subscriptionService.checkout(userId, body);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(
            @RequestBody BillingConfirmRequestDTO body,
            HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            UserSubscriptionDTO response = subscriptionService.confirm(userId, body);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(HttpServletRequest request) {
        try {
            String userId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            return ResponseEntity.ok(subscriptionService.cancel(userId));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @ExceptionHandler(SkillamaAuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(SkillamaAuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }
}
