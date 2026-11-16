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
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/run-batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runBatchNow(HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(userId(request));
            adminPermissionService.requirePermission(userId(request), AdminModule.OUTREACH, AdminPermissionAction.UPDATE);
            PrwatechOutreachService.BatchRunResult result = outreachService.processDailyBatch();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Batch job finished");
            body.put("submitted", result.submitted());
            body.put("failed", result.failed());
            body.put("picked", result.picked());
            body.put("campaignPaused", result.campaignPaused());
            body.put("note", result.note() != null ? result.note() : "");
            return ResponseEntity.ok(new ApiResponse<>(200, body));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
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