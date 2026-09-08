package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.OrgFeaturesDTO;
import com.prwatech.skillama.dto.OrganizationActivityLogDTO;
import com.prwatech.skillama.dto.OrganizationDTO;
import com.prwatech.skillama.dto.UpdateOrgBrandingRequestDTO;
import com.prwatech.skillama.dto.UpdateOrgSecurityRequestDTO;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.OrgActivityService;
import com.prwatech.skillama.service.OrgFeatureService;
import com.prwatech.skillama.service.OrganizationService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/skillama/api/org/settings")
@RequiredArgsConstructor
public class OrgSettingsController {

    private final OrganizationService organizationService;
    private final OrgFeatureService orgFeatureService;
    private final OrgActivityService orgActivityService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final TenantSecurityService tenantSecurityService;

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<OrgFeaturesDTO>> getFeatures(HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            if (actor.getOrganizationId() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            List<String> codes = orgFeatureService.listEnabledFeatureCodes(actor.getOrganizationId());
            return ResponseEntity.ok(new ApiResponse<>(200, OrgFeaturesDTO.builder()
                    .enabledFeatures(codes)
                    .build()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/security")
    public ResponseEntity<ApiResponse<OrganizationDTO>> getSecurity(HttpServletRequest request) {
        try {
            User actor = requireOrgAdminActor(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    organizationService.getOrganization(actor.getOrganizationId())));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/security")
    public ResponseEntity<ApiResponse<OrganizationDTO>> updateSecurity(
            @RequestBody UpdateOrgSecurityRequestDTO body,
            HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            if (actor.getOrganizationId() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.ok(new ApiResponse<>(200,
                    organizationService.updateSecurity(actor.getOrganizationId(), body, actor)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<Page<OrganizationActivityLogDTO>>> getActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            User actor = requireOrgAdminActor(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    orgActivityService.listActivity(actor.getOrganizationId(), page, size)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @GetMapping("/branding")
    public ResponseEntity<ApiResponse<OrganizationDTO>> getBranding(HttpServletRequest request) {
        try {
            User actor = requireOrgAdminActor(request);
            return ResponseEntity.ok(new ApiResponse<>(200,
                    organizationService.getOrganization(actor.getOrganizationId())));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    @PutMapping("/branding")
    public ResponseEntity<ApiResponse<OrganizationDTO>> updateBranding(
            @RequestBody UpdateOrgBrandingRequestDTO body,
            HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            if (actor.getOrganizationId() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.ok(new ApiResponse<>(200,
                    organizationService.updateBranding(actor.getOrganizationId(), body, actor)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }

    /**
     * Settings reads return {@link OrganizationDTO}, which carries contract, seat and security
     * detail, so they are owner/admin-only. Rank-and-file members use {@code /features} instead.
     */
    private User requireOrgAdminActor(HttpServletRequest request) {
        User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
        if (actor.getOrganizationId() == null) {
            throw new IllegalStateException("Organization context required");
        }
        if (TenantSecurityService.isPlatformStaff(actor)) {
            return actor;
        }
        OrgRole role = actor.getOrgRole();
        if (role != OrgRole.ORG_OWNER && role != OrgRole.ORG_ADMIN) {
            throw new IllegalStateException("Insufficient organization permissions");
        }
        return actor;
    }
}
