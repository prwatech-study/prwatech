package com.prwatech.skillama.controller;

import com.prwatech.skillama.dto.ApiResponse;
import com.prwatech.skillama.dto.OrgHierarchyNodeDTO;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.OrganizationService;
import com.prwatech.skillama.service.OrgFeatureService;
import com.prwatech.skillama.service.SkillamaAuthSupport;
import com.prwatech.skillama.service.TenantSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/skillama/api/org/hierarchy")
@RequiredArgsConstructor
public class OrgHierarchyController {

    private final OrganizationService organizationService;
    private final OrgFeatureService orgFeatureService;
    private final SkillamaAuthSupport skillamaAuthSupport;
    private final TenantSecurityService tenantSecurityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrgHierarchyNodeDTO>>> getHierarchy(HttpServletRequest request) {
        try {
            User actor = tenantSecurityService.requireUser(skillamaAuthSupport.resolveUserIdFromRequest(request));
            if (actor.getOrganizationId() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            if (!orgFeatureService.isEnabled(actor.getOrganizationId(), "org_hierarchy")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            OrgRole role = actor.getOrgRole();
            if (role != OrgRole.ORG_OWNER && role != OrgRole.ORG_ADMIN && role != OrgRole.MANAGER
                    && !TenantSecurityService.isPlatformStaff(actor)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, null));
            }
            return ResponseEntity.ok(new ApiResponse<>(200,
                    organizationService.getHierarchyForActor(actor)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, null));
        }
    }
}
