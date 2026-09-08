package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AssignmentResponseDTO;
import com.prwatech.skillama.dto.OrgAssignCoursesRequestDTO;
import com.prwatech.skillama.dto.OrgCourseOptionDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrgAssignmentService {

    private final SkillamaUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final TenantSecurityService tenantSecurityService;
    private final OrgFeatureService orgFeatureService;
    private final AdminService adminService;

    @Transactional
    public AssignmentResponseDTO assignCourses(User actor, String targetUserId, OrgAssignCoursesRequestDTO request) {
        Organization org = requireActorOrg(actor);
        assertCanAssign(actor);
        User target = requireOrgUser(org.getId(), targetUserId);
        if (target.getOrgRole() == OrgRole.ORG_OWNER || target.getOrgRole() == OrgRole.ORG_ADMIN) {
            throw new IllegalArgumentException("Course assignment applies to learners only");
        }
        if (request == null || request.getCourseIds() == null || request.getCourseIds().isEmpty()) {
            throw new IllegalArgumentException("courseIds are required");
        }
        return adminService.assignCoursesForOrgUser(targetUserId, request.getCourseIds(), actor.getId());
    }

    @Transactional
    public void unassignCourse(User actor, String targetUserId, String courseId) {
        Organization org = requireActorOrg(actor);
        assertCanAssign(actor);
        requireOrgUser(org.getId(), targetUserId);
        adminService.unassignCourseForOrgUser(targetUserId, courseId, actor.getId());
    }

    public List<OrgCourseOptionDTO> listAssignableCourses(User actor) {
        assertCanAssign(actor);
        return courseRepository.findAll().stream()
                .filter(c -> c.getDeletedAt() == null)
                .sorted(Comparator.comparing(Course::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(c -> OrgCourseOptionDTO.builder().courseId(c.getId()).name(c.getName()).build())
                .toList();
    }

    private void assertCanAssign(User actor) {
        if (TenantSecurityService.isPlatformStaff(actor)) {
            return;
        }
        OrgRole role = actor.getOrgRole();
        if (role != OrgRole.ORG_OWNER && role != OrgRole.ORG_ADMIN) {
            throw new IllegalStateException("Insufficient organization permissions");
        }
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

    private User requireOrgUser(String organizationId, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        tenantSecurityService.assertUserInOrg(user, organizationId);
        return user;
    }
}
