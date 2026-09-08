package com.prwatech.skillama.service;

import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationStatus;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantSecurityService {

    private final OrganizationRepository organizationRepository;
    private final SkillamaUserRepository userRepository;

    public Organization requireActiveOrganization(String organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        if (org.getStatus() == OrganizationStatus.SUSPENDED) {
            throw new IllegalStateException("Organization account is suspended. Contact your administrator.");
        }
        if (org.getStatus() == OrganizationStatus.ARCHIVED) {
            throw new IllegalStateException("Organization account is archived.");
        }
        if (org.getStatus() == OrganizationStatus.PROVISIONING) {
            throw new IllegalStateException("Organization is not yet active.");
        }
        return org;
    }

    public Organization requireOrganizationBySlug(String slug) {
        return organizationRepository.findBySlug(slug.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    public void assertSameOrg(User actor, User target) {
        if (actor == null || target == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (isPlatformStaff(actor)) {
            return;
        }
        if (actor.getOrganizationId() == null || target.getOrganizationId() == null) {
            throw new IllegalStateException("Cross-organization access denied");
        }
        if (!actor.getOrganizationId().equals(target.getOrganizationId())) {
            throw new IllegalStateException("Cross-organization access denied");
        }
    }

    public void assertUserInOrg(User user, String organizationId) {
        if (user == null || organizationId == null) {
            throw new IllegalArgumentException("Invalid organization context");
        }
        if (isPlatformStaff(user)) {
            return;
        }
        if (user.getOrganizationId() == null || !user.getOrganizationId().equals(organizationId)) {
            throw new IllegalStateException("Organization mismatch");
        }
    }

    public static boolean isPlatformStaff(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        return user.getRole() == User.UserRole.ADMIN || user.getRole() == User.UserRole.OWNER;
    }

    public User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
