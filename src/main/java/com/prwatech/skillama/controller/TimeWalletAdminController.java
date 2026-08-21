package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.TimeWalletAdjustRequestDTO;
import com.prwatech.skillama.dto.TimeWalletDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.service.AdminAuditService;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TimeWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/** Admin management of B2B time-based wallets. OWNER only, mirroring credits/adjust. */
@RestController
@RequestMapping("/skillama/api/admin/users")
@RequiredArgsConstructor
public class TimeWalletAdminController {

    private final TimeWalletService timeWalletService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final AdminAuditService adminAuditService;

    @GetMapping("/{userId}/time-wallet")
    public ResponseEntity<ApiResponse<TimeWalletDTO>> getTimeWallet(
            @PathVariable String userId, HttpServletRequest httpRequest) {
        try {
            adminPermissionService.requireOwner(skillamaAuthSupport.resolveUserIdFromRequest(httpRequest));
            return ResponseEntity.ok(new ApiResponse<>(200, timeWalletService.getStatus(userId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (isOwnerForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping("/{userId}/time-wallet/adjust")
    public ResponseEntity<ApiResponse<TimeWalletDTO>> adjustTimeWallet(
            @PathVariable String userId,
            @RequestBody TimeWalletAdjustRequestDTO body,
            HttpServletRequest httpRequest) {
        try {
            String adminId = skillamaAuthSupport.resolveUserIdFromRequest(httpRequest);
            adminPermissionService.requireOwner(adminId);
            TimeWalletDTO result = timeWalletService.adjust(userId, body, adminId);
            adminAuditService.log(adminId, "TIME_WALLET_ADJUST", "USER", userId,
                    "Time wallet adjust deltaMinutes=" + body.getDeltaMinutes()
                            + " allocatedMinutes → " + result.getAllocatedMinutes()
                            + " (consumedMinutes=" + result.getConsumedMinutes() + ")"
                            + " reason=" + body.getReason(), null);
            return ResponseEntity.ok(new ApiResponse<>(200, result));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            if (isOwnerForbidden(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    private boolean isOwnerForbidden(RuntimeException e) {
        return e.getMessage() != null && e.getMessage().contains("Owner access required");
    }
}
