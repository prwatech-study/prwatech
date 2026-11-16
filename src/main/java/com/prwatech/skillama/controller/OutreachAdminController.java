package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.OutreachCampaignUpdateDTO;
import com.prwatech.skillama.dto.OutreachSummaryDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.outreach.OutreachCampaign;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.outreach.PrwatechOutreachService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/skillama/api/admin/outreach")
@RequiredArgsConstructor
public class OutreachAdminController {

    private final PrwatechOutreachService outreachService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaUserRepository userRepository;
    private final SkillamaAuthSupport skillamaAuthSupport;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<OutreachSummaryDTO>> summary(HttpServletRequest request) {
        try {
            assertRead(request);
            return ResponseEntity.ok(new ApiResponse<>(200, outreachService.getSummary()));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Map<String, Long>>> sync(HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(userId(request));
            adminPermissionService.requirePermission(userId(request), AdminModule.OUTREACH, AdminPermissionAction.UPDATE);
            PrwatechOutreachService.SyncResult r = outreachService.syncLegacyRecipients();
            return ResponseEntity.ok(new ApiResponse<>(200, Map.of(
                    "queuedNew", r.queuedNew(),
                    "eligibleLegacy", r.eligibleLegacy())));
        } catch (IllegalStateException e) {
            return outreachDisabled(e);
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PatchMapping("/campaign")
    public ResponseEntity<ApiResponse<OutreachCampaign>> updateCampaign(
            HttpServletRequest request, @RequestBody OutreachCampaignUpdateDTO body) {
        try {
            adminPermissionService.requireOwner(userId(request));
            adminPermissionService.requirePermission(userId(request), AdminModule.OUTREACH, AdminPermissionAction.UPDATE);
            return ResponseEntity.ok(new ApiResponse<>(200, outreachService.updateCampaign(body)));
        } catch (IllegalStateException e) {
            return outreachDisabled(e);
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendTest(HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(userId(request));
            adminPermissionService.requirePermission(userId(request), AdminModule.OUTREACH, AdminPermissionAction.UPDATE);
            User owner = userRepository.findById(userId(request))
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (owner.getEmail() == null || owner.getEmail().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(400, Map.of("message", "Owner account has no email")));
            }
            outreachService.sendTestToOwner(owner.getEmail());
            return ResponseEntity.ok(new ApiResponse<>(200, Map.of("message", "Test invite sent")));
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("disabled")) {
                return outreachDisabled(e);
            }
            String msg = e.getMessage() != null ? e.getMessage() : "SMTP send failed";
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ApiResponse<>(502, Map.of("message", msg)));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/run-batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runBatchNow(HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(userId(request));
            adminPermissionService.requirePermission(userId(request), AdminModule.OUTREACH, AdminPermissionAction.UPDATE);
            PrwatechOutreachService.BatchStartResult result = outreachService.requestDailyBatchStart();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", result.message());
            body.put("outcome", result.outcome().name());
            body.put("batchInProgress", result.outcome()
                    == PrwatechOutreachService.BatchStartOutcome.STARTED);
            return switch (result.outcome()) {
                case STARTED -> ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponse<>(202, body));
                case ALREADY_RUNNING -> ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(409, body));
                case DISABLED, NOT_RUNNING -> ResponseEntity.ok(new ApiResponse<>(200, body));
            };
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    private ResponseEntity<ApiResponse<Map<String, String>>> outreachDisabled(IllegalStateException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Outreach is disabled in admin";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(400, Map.of("message", msg)));
    }

    private void assertRead(HttpServletRequest request) {
        adminPermissionService.requirePermission(userId(request), AdminModule.OUTREACH, AdminPermissionAction.READ);
    }

    private String userId(HttpServletRequest request) {
        return skillamaAuthSupport.resolveUserIdFromRequest(request);
    }

    private <T> ResponseEntity<ApiResponse<T>> forbiddenOrUnauthorized(RuntimeException e) {
        if (e.getMessage() != null && (e.getMessage().contains("Owner") || e.getMessage().contains("Insufficient permission"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
    }
}