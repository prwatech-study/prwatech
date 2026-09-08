package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.OrgHierarchyNodeDTO;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrgHierarchyService {

    private final SkillamaUserRepository userRepository;

    public Set<String> getVisibleUserIds(User actor) {
        if (actor == null) {
            return Set.of();
        }
        if (TenantSecurityService.isPlatformStaff(actor)) {
            return userRepository.findAll().stream()
                    .filter(u -> actor.getOrganizationId() == null
                            || actor.getOrganizationId().equals(u.getOrganizationId()))
                    .map(User::getId)
                    .collect(java.util.stream.Collectors.toSet());
        }
        if (actor.getOrganizationId() == null) {
            return Set.of(actor.getId());
        }
        OrgRole role = actor.getOrgRole() != null ? actor.getOrgRole() : OrgRole.LEARNER;
        if (role == OrgRole.ORG_OWNER || role == OrgRole.ORG_ADMIN) {
            return userRepository.findByOrganizationId(actor.getOrganizationId()).stream()
                    .map(User::getId)
                    .collect(java.util.stream.Collectors.toSet());
        }
        if (role == OrgRole.MANAGER) {
            return collectDescendantIds(actor.getOrganizationId(), actor.getId());
        }
        return Set.of(actor.getId());
    }

    public void validateManagerAssignment(String organizationId, String userId, String managerUserId) {
        if (managerUserId == null || managerUserId.isBlank()) {
            return;
        }
        if (userId != null && userId.equals(managerUserId)) {
            throw new IllegalArgumentException("User cannot be their own manager");
        }
        User manager = userRepository.findById(managerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Manager not found"));
        if (manager.getOrganizationId() == null || !manager.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Manager must belong to the same organization");
        }
        if (userId == null) {
            return;
        }
        Set<String> descendants = collectDescendantIds(organizationId, userId);
        if (descendants.contains(managerUserId)) {
            throw new IllegalArgumentException("Manager cannot be a descendant of the user");
        }
    }

    private Set<String> collectDescendantIds(String organizationId, String managerId) {
        List<User> orgUsers = userRepository.findByOrganizationId(organizationId);
        Set<String> result = new HashSet<>();
        collectDescendantsRecursive(managerId, orgUsers, result);
        return result;
    }

    private void collectDescendantsRecursive(String managerId, List<User> orgUsers, Set<String> result) {
        for (User user : orgUsers) {
            if (managerId.equals(user.getManagerUserId()) && result.add(user.getId())) {
                collectDescendantsRecursive(user.getId(), orgUsers, result);
            }
        }
    }

    public List<User> getDirectReports(String organizationId, String managerId) {
        return userRepository.findByOrganizationIdAndManagerUserId(organizationId, managerId);
    }

    public List<OrgHierarchyNodeDTO> buildHierarchyTree(String organizationId) {
        return buildHierarchyTree(organizationId, null);
    }

    /**
     * Builds the tree from {@code rootUserId} downwards, or from the org roots when it is null.
     * Rooting the tree is what keeps a manager's view inside their own subtree — without it the
     * whole org (including peers and superiors, with their emails) would be returned.
     */
    public List<OrgHierarchyNodeDTO> buildHierarchyTree(String organizationId, String rootUserId) {
        List<User> users = userRepository.findByOrganizationId(organizationId);
        Map<String, List<User>> byManager = new HashMap<>();
        for (User user : users) {
            String managerId = user.getManagerUserId() != null ? user.getManagerUserId() : "";
            byManager.computeIfAbsent(managerId, k -> new ArrayList<>()).add(user);
        }
        if (rootUserId == null) {
            return buildNodes("", byManager);
        }
        return users.stream()
                .filter(u -> rootUserId.equals(u.getId()))
                .findFirst()
                .map(root -> List.of(toNode(root, byManager)))
                .orElseGet(List::of);
    }

    private List<OrgHierarchyNodeDTO> buildNodes(String managerId, Map<String, List<User>> byManager) {
        return buildNodes(managerId, byManager, new HashSet<>());
    }

    private List<OrgHierarchyNodeDTO> buildNodes(
            String managerId, Map<String, List<User>> byManager, Set<String> visited) {
        List<User> children = byManager.getOrDefault(managerId, List.of());
        List<OrgHierarchyNodeDTO> nodes = new ArrayList<>();
        for (User user : children) {
            // A cycle already in the data would otherwise recurse until the stack blows.
            if (!visited.add(user.getId())) {
                continue;
            }
            nodes.add(node(user, buildNodes(user.getId(), byManager, visited)));
        }
        return nodes;
    }

    private OrgHierarchyNodeDTO toNode(User root, Map<String, List<User>> byManager) {
        Set<String> visited = new HashSet<>();
        visited.add(root.getId());
        return node(root, buildNodes(root.getId(), byManager, visited));
    }

    private OrgHierarchyNodeDTO node(User user, List<OrgHierarchyNodeDTO> reports) {
        return OrgHierarchyNodeDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .orgRole(user.getOrgRole() != null ? user.getOrgRole() : OrgRole.LEARNER)
                .department(user.getDepartment())
                .managerUserId(user.getManagerUserId())
                .reports(reports)
                .build();
    }
}
