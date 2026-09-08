package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.OrgUserService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/skillama/api/org/users")
@RequiredArgsConstructor
public class OrgUserController {

    private final OrgUserService orgUserService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final TenantSecurityService tenantSecurityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrgUserDTO>>> list(HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, orgUserService.listUsers(actor)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrgUserDTO>> create(
            @RequestBody CreateOrgUserRequestDTO body, HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, orgUserService.createUser(actor, body)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<OrgUserDTO>> update(
            @PathVariable String userId,
            @RequestBody UpdateOrgUserRequestDTO body,
            HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, orgUserService.updateUser(actor, userId, body)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<BulkImportOrgUsersResultDTO>> bulkImport(
            @RequestBody BulkImportOrgUsersRequestDTO body, HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, orgUserService.bulkImportUsers(actor, body)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PostMapping("/{userId}/transfer-ownership")
    public ResponseEntity<ApiResponse<OrgUserDTO>> transferOwnership(
            @PathVariable String userId, HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            return ResponseEntity.ok(new ApiResponse<>(200, orgUserService.transferOwnership(actor, userId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }
}
