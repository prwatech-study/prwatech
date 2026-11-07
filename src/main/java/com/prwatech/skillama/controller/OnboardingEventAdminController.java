package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.OnboardingEventDetailDTO;
import com.prwatech.skillama.dto.OnboardingEventSummaryDTO;
import com.prwatech.skillama.dto.UpsertOnboardingEventRequest;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.EventFeatureCatalog;
import com.prwatech.skillama.service.OnboardingEventService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/skillama/api/admin/events")
@RequiredArgsConstructor
public class OnboardingEventAdminController {

    private final OnboardingEventService onboardingEventService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<List<EventFeatureCatalog.Feature>>> features(HttpServletRequest request) {
        try {
            require(request, AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200, onboardingEventService.featureChoices()));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OnboardingEventSummaryDTO>>> list(HttpServletRequest request) {
        try {
            require(request, AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200, onboardingEventService.list()));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<?> get(@PathVariable String eventId, HttpServletRequest request) {
        try {
            require(request, AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200, onboardingEventService.get(eventId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody UpsertOnboardingEventRequest body, HttpServletRequest request) {
        try {
            String actorId = require(request, AdminPermissionAction.CREATE);
            return ResponseEntity.ok(new ApiResponse<>(200, onboardingEventService.create(body, actorId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<?> update(
            @PathVariable String eventId,
            @RequestBody UpsertOnboardingEventRequest body,
            HttpServletRequest request) {
        try {
            String actorId = require(request, AdminPermissionAction.UPDATE);
            return ResponseEntity.ok(new ApiResponse<>(200, onboardingEventService.update(eventId, body, actorId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/{eventId}/publish")
    public ResponseEntity<?> publish(@PathVariable String eventId, HttpServletRequest request) {
        return action(eventId, request, AdminPermissionAction.UPDATE, onboardingEventService::publish);
    }

    @PostMapping("/{eventId}/close")
    public ResponseEntity<?> close(@PathVariable String eventId, HttpServletRequest request) {
        return action(eventId, request, AdminPermissionAction.UPDATE, onboardingEventService::closeRegistration);
    }

    @PostMapping("/{eventId}/reopen")
    public ResponseEntity<?> reopen(@PathVariable String eventId, HttpServletRequest request) {
        return action(eventId, request, AdminPermissionAction.UPDATE, onboardingEventService::reopen);
    }

    @PostMapping("/{eventId}/end")
    public ResponseEntity<?> end(@PathVariable String eventId, HttpServletRequest request) {
        return action(eventId, request, AdminPermissionAction.UPDATE, onboardingEventService::end);
    }

    private ResponseEntity<?> action(
            String eventId,
            HttpServletRequest request,
            AdminPermissionAction permission,
            EventAction action) {
        try {
            String actorId = require(request, permission);
            return ResponseEntity.ok(new ApiResponse<OnboardingEventDetailDTO>(200, action.apply(eventId, actorId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    private String require(HttpServletRequest request, AdminPermissionAction action) {
        String adminId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        adminPermissionService.requirePermission(adminId, AdminModule.EVENTS, action);
        return adminId;
    }

    private <T> ResponseEntity<ApiResponse<T>> forbiddenOrUnauthorized(RuntimeException e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        if (message.contains("Insufficient permission") || message.contains("Owner access required")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
    }

    @FunctionalInterface
    private interface EventAction {
        OnboardingEventDetailDTO apply(String eventId, String actorId);
    }
}
