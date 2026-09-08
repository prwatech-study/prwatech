package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CourseEnrollmentRequestDTO;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.CourseEnrollmentRequest;
import com.prwatech.skillama.model.OrgModule;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrgEnrollmentRequestService {

    private final CourseEnrollmentRequestService courseEnrollmentRequestService;
    private final TenantSecurityService tenantSecurityService;
    private final OrgPermissionService orgPermissionService;
    private final OrgFeatureService orgFeatureService;

    public List<CourseEnrollmentRequestDTO> list(User actor, CourseEnrollmentRequest.RequestStatus status) {
        Organization org = requireActorOrg(actor);
        assertCanManage(actor, AdminPermissionAction.READ);
        return courseEnrollmentRequestService.listForOrganization(org.getId(), status);
    }

    @Transactional
    public CourseEnrollmentRequestDTO approve(User actor, String requestId) {
        Organization org = requireActorOrg(actor);
        assertCanManage(actor, AdminPermissionAction.CREATE);
        return courseEnrollmentRequestService.approveForOrganization(requestId, actor.getId(), org.getId());
    }

    @Transactional
    public CourseEnrollmentRequestDTO deny(User actor, String requestId, String reason) {
        Organization org = requireActorOrg(actor);
        assertCanManage(actor, AdminPermissionAction.CREATE);
        return courseEnrollmentRequestService.denyForOrganization(
                requestId, actor.getId(), org.getId(), reason);
    }

    private void assertCanManage(User actor, AdminPermissionAction action) {
        OrgRole role = actor.getOrgRole();
        if (role != OrgRole.ORG_OWNER && role != OrgRole.ORG_ADMIN) {
            throw new IllegalStateException("Insufficient organization permissions");
        }
        orgPermissionService.require(actor, OrgModule.ASSIGNMENTS, action);
        if (!orgFeatureService.isEnabled(actor.getOrganizationId(), "org_user_management")) {
            throw new IllegalStateException("User management is not enabled for this organization");
        }
    }

    private Organization requireActorOrg(User actor) {
        if (actor.getOrganizationId() == null) {
            throw new IllegalStateException("Organization context required");
        }
        return tenantSecurityService.requireActiveOrganization(actor.getOrganizationId());
    }
}
