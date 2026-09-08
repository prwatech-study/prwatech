package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.OrganizationStatus;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.AdminPermissionService;
import com.prwatech.skillama.service.OrgActivityService;
import com.prwatech.skillama.service.OrganizationService;
import com.prwatech.skillama.service.OrgUserService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/skillama/api/admin/organizations")
@RequiredArgsConstructor
public class OrganizationAdminController {

    private final OrganizationService organizationService;
    private final OrgUserService orgUserService;
    private final OrgActivityService orgActivityService;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final TenantSecurityService tenantSecurityService;

    @GetMapping("/expiring")
    public ResponseEntity<ApiResponse<List<OrganizationDTO>>> listExpiring(
            @RequestParam(defaultValue = "30") int days,
            HttpServletRequest request) {
        try {
            requireRead(request);
            return ResponseEntity.ok(new ApiResponse<>(200, organizationService.listExpiringOrganizations(days)));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrganizationDTO>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrganizationStatus status,
            HttpServletRequest request) {
        try {
            requireRead(request);
            return ResponseEntity.ok(new ApiResponse<>(200, organizationService.listOrganizations(page, size, status)));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @GetMapping("/{organizationId}/contract")
    public ResponseEntity<ApiResponse<OrganizationContractDTO>> getContract(
            @PathVariable String organizationId, HttpServletRequest request) {
        try {
            requireRead(request);
            return ResponseEntity.ok(new ApiResponse<>(200, organizationService.getContract(organizationId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<OrganizationDTO>> get(
            @PathVariable String organizationId, HttpServletRequest request) {
        try {
            requireRead(request);
            return ResponseEntity.ok(new ApiResponse<>(200, organizationService.getOrganization(organizationId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationDTO>> create(
            @RequestBody CreateOrganizationRequestDTO body, HttpServletRequest request) {
        try {
            String adminId = requireCreate(request);
            return ResponseEntity.ok(new ApiResponse<>(200, organizationService.createOrganization(body, adminId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/{organizationId}/contracts/renew")
    public ResponseEntity<ApiResponse<OrganizationContractDTO>> renew(
            @PathVariable String organizationId,
            @RequestBody RenewOrganizationContractRequestDTO body,
            HttpServletRequest request) {
        try {
            String adminId = requireUpdate(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    organizationService.renewContract(organizationId, body, adminId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @GetMapping("/{organizationId}/hierarchy")
    public ResponseEntity<ApiResponse<List<OrgHierarchyNodeDTO>>> hierarchy(
            @PathVariable String organizationId, HttpServletRequest request) {
        try {
            requireRead(request);
            return ResponseEntity.ok(new ApiResponse<>(200, organizationService.getHierarchy(organizationId)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @GetMapping("/{organizationId}/activity")
    public ResponseEntity<ApiResponse<Page<OrganizationActivityLogDTO>>> activity(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            requireRead(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    orgActivityService.listActivity(organizationId, page, size)));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PostMapping("/{organizationId}/users")
    public ResponseEntity<ApiResponse<OrgUserDTO>> createUser(
            @PathVariable String organizationId,
            @RequestBody CreateOrgUserRequestDTO body,
            HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            requireCreate(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    orgUserService.createUserForOrganization(organizationId, body, actor)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    @PutMapping("/{organizationId}/users/{userId}")
    public ResponseEntity<ApiResponse<OrgUserDTO>> updateUser(
            @PathVariable String organizationId,
            @PathVariable String userId,
            @RequestBody UpdateOrgUserRequestDTO body,
            HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            requireUpdate(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    orgUserService.updateUserForOrganization(organizationId, userId, body, actor)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, null));
        } catch (RuntimeException e) {
            return forbiddenOrUnauthorized(e);
        }
    }

    private void requireRead(HttpServletRequest request) {
        adminPermissionService.requirePermission(
                skillamaAuthSupport.resolveUserIdFromRequest(request),
                AdminModule.ORGANIZATIONS,
                AdminPermissionAction.READ);
    }

    private String requireCreate(HttpServletRequest request) {
        String adminId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        adminPermissionService.requirePermission(adminId, AdminModule.ORGANIZATIONS, AdminPermissionAction.CREATE);
        return adminId;
    }

    private String requireUpdate(HttpServletRequest request) {
        String adminId = skillamaAuthSupport.resolveUserIdFromRequest(request);
        adminPermissionService.requirePermission(adminId, AdminModule.ORGANIZATIONS, AdminPermissionAction.UPDATE);
        return adminId;
    }

    private <T> ResponseEntity<ApiResponse<T>> forbiddenOrUnauthorized(RuntimeException e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        if (message.contains("Insufficient permission") || message.contains("Owner access required")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
    }
}
