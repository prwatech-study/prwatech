package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.AiUsagePlatformSummaryDTO;
import com.prwatech.skillama.dto.AiUsageSettingsDTO;
import com.prwatech.skillama.dto.AiUsageUserDetailDTO;
import com.prwatech.skillama.dto.AiUsageUserRowDTO;
import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.EfficiencyAssumptionsDTO;
import com.prwatech.skillama.dto.EfficiencyEstimateDTO;
import com.prwatech.skillama.dto.UpdateAiUsageSettingsDTO;
import com.prwatech.skillama.dto.UpdateEfficiencyAssumptionsDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.AiUsageService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/skillama/api/admin/ai-usage")
@RequiredArgsConstructor
public class AiUsageAdminController {

    private final AiUsageService aiUsageService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final AdminPermissionService adminPermissionService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AiUsagePlatformSummaryDTO>> getSummary(
            @RequestParam(defaultValue = "month") String period,
            HttpServletRequest request) {
        try {
            adminPermissionService.requirePermission(
                    skillamaAuthSupport.resolveUserIdFromRequest(request),
                    AdminModule.AI_USAGE,
                    AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200, aiUsageService.getPlatformSummary(period)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<AiUsageUserRowDTO>>> listUsers(
            @RequestParam(defaultValue = "month") String period,
            HttpServletRequest request) {
        try {
            adminPermissionService.requirePermission(
                    skillamaAuthSupport.resolveUserIdFromRequest(request),
                    AdminModule.AI_USAGE,
                    AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200, aiUsageService.listUserUsage(period)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AiUsageUserDetailDTO>> getUserDetail(
            @PathVariable String userId,
            @RequestParam(defaultValue = "month") String period,
            HttpServletRequest request) {
        try {
            adminPermissionService.requirePermission(
                    skillamaAuthSupport.resolveUserIdFromRequest(request),
                    AdminModule.AI_USAGE,
                    AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200, aiUsageService.getUserUsageDetail(userId, period)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<AiUsageSettingsDTO>> getSettings(HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, aiUsageService.getSettingsDto()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<AiUsageSettingsDTO>> updateSettings(
            @RequestBody UpdateAiUsageSettingsDTO body,
            HttpServletRequest request) {
        try {
            String ownerId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            adminPermissionService.requireOwner(ownerId);
            return ResponseEntity.ok(new ApiResponse<>(200, aiUsageService.updateSettings(body, ownerId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/efficiency-estimate")
    public ResponseEntity<ApiResponse<EfficiencyEstimateDTO>> getEfficiencyEstimate(
            @RequestParam(defaultValue = "month") String period,
            HttpServletRequest request) {
        try {
            adminPermissionService.requirePermission(
                    skillamaAuthSupport.resolveUserIdFromRequest(request),
                    AdminModule.AI_USAGE,
                    AdminPermissionAction.READ);
            return ResponseEntity.ok(new ApiResponse<>(200, aiUsageService.getEfficiencyEstimate(period)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/efficiency-settings")
    public ResponseEntity<ApiResponse<EfficiencyAssumptionsDTO>> getEfficiencySettings(HttpServletRequest request) {
        try {
            adminPermissionService.requireOwner(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, aiUsageService.getEfficiencyAssumptionsDto()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/efficiency-settings")
    public ResponseEntity<ApiResponse<EfficiencyAssumptionsDTO>> updateEfficiencySettings(
            @RequestBody UpdateEfficiencyAssumptionsDTO body,
            HttpServletRequest request) {
        try {
            String ownerId = skillamaAuthSupport.resolveUserIdFromRequest(request);
            adminPermissionService.requireOwner(ownerId);
            return ResponseEntity.ok(new ApiResponse<>(200, aiUsageService.updateEfficiencyAssumptions(body, ownerId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }
}
