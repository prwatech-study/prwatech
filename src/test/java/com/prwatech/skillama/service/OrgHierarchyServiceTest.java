package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.OrgHierarchyNodeDTO;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Manager scoping is what keeps one org's reporting line from seeing another's, and what
 * keeps a manager from reading peers above or beside them. These tests pin the subtree
 * boundary and the cycle guards that protect it.
 */
@ExtendWith(MockitoExtension.class)
class OrgHierarchyServiceTest {

    private static final String ORG = "org-a";

    @Mock private SkillamaUserRepository userRepository;
    @InjectMocks private OrgHierarchyService orgHierarchyService;

    /**
     * owner
     *  └── manager (m1)
     *        ├── lead (m2)
     *        │     └── junior (u3)
     *        └── direct (u2)
     * peer (u4) reports to owner, i.e. outside m1's subtree.
     */
    private List<User> orgTree() {
        return List.of(
                orgUser("owner", null, OrgRole.ORG_OWNER),
                orgUser("m1", "owner", OrgRole.MANAGER),
                orgUser("m2", "m1", OrgRole.MANAGER),
                orgUser("u3", "m2", OrgRole.LEARNER),
                orgUser("u2", "m1", OrgRole.LEARNER),
                orgUser("u4", "owner", OrgRole.LEARNER));
    }

    private User orgUser(String id, String managerId, OrgRole role) {
        return User.builder()
                .id(id)
                .name(id)
                .email(id + "@acme.com")
                .organizationId(ORG)
                .managerUserId(managerId)
                .role(User.UserRole.USER)
                .orgRole(role)
                .build();
    }

    @Test
    void getVisibleUserIds_managerSeesDirectAndIndirectReportsOnly() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());
        User manager = orgUser("m1", "owner", OrgRole.MANAGER);

        Set<String> visible = orgHierarchyService.getVisibleUserIds(manager);

        assertEquals(Set.of("m2", "u3", "u2"), visible);
    }

    @Test
    void getVisibleUserIds_managerCannotSeeOwnManagerOrPeers() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());
        User manager = orgUser("m1", "owner", OrgRole.MANAGER);

        Set<String> visible = orgHierarchyService.getVisibleUserIds(manager);

        assertTrue(visible.stream().noneMatch(id -> id.equals("owner") || id.equals("u4")));
    }

    @Test
    void getVisibleUserIds_orgOwnerSeesWholeOrg() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());
        User owner = orgUser("owner", null, OrgRole.ORG_OWNER);

        Set<String> visible = orgHierarchyService.getVisibleUserIds(owner);

        assertEquals(Set.of("owner", "m1", "m2", "u3", "u2", "u4"), visible);
    }

    @Test
    void getVisibleUserIds_orgAdminSeesWholeOrg() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());
        User admin = orgUser("a1", "owner", OrgRole.ORG_ADMIN);

        assertEquals(6, orgHierarchyService.getVisibleUserIds(admin).size());
    }

    @Test
    void getVisibleUserIds_learnerSeesOnlySelf() {
        User learner = orgUser("u2", "m1", OrgRole.LEARNER);

        assertEquals(Set.of("u2"), orgHierarchyService.getVisibleUserIds(learner));
    }

    @Test
    void getVisibleUserIds_missingOrgRoleIsTreatedAsLearner() {
        User user = User.builder().id("u9").organizationId(ORG).role(User.UserRole.USER).build();

        assertEquals(Set.of("u9"), orgHierarchyService.getVisibleUserIds(user));
    }

    @Test
    void getVisibleUserIds_userWithoutOrgSeesOnlySelf() {
        User solo = User.builder().id("s1").role(User.UserRole.USER).orgRole(OrgRole.MANAGER).build();

        assertEquals(Set.of("s1"), orgHierarchyService.getVisibleUserIds(solo));
    }

    @Test
    void getVisibleUserIds_nullActorSeesNothing() {
        assertTrue(orgHierarchyService.getVisibleUserIds(null).isEmpty());
    }

    @Test
    void validateManagerAssignment_rejectsSelfAsManager() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orgHierarchyService.validateManagerAssignment(ORG, "m1", "m1"));

        assertTrue(ex.getMessage().contains("own manager"));
    }

    @Test
    void validateManagerAssignment_rejectsManagerFromAnotherOrg() {
        User foreign = User.builder().id("x1").organizationId("org-b").build();
        when(userRepository.findById("x1")).thenReturn(Optional.of(foreign));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orgHierarchyService.validateManagerAssignment(ORG, "u2", "x1"));

        assertTrue(ex.getMessage().contains("same organization"));
    }

    @Test
    void validateManagerAssignment_rejectsManagerWithNoOrg() {
        User orphan = User.builder().id("x2").build();
        when(userRepository.findById("x2")).thenReturn(Optional.of(orphan));

        assertThrows(
                IllegalArgumentException.class,
                () -> orgHierarchyService.validateManagerAssignment(ORG, "u2", "x2"));
    }

    @Test
    void validateManagerAssignment_rejectsMissingManager() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> orgHierarchyService.validateManagerAssignment(ORG, "u2", "ghost"));
    }

    @Test
    void validateManagerAssignment_rejectsDirectReportAsManager() {
        when(userRepository.findById("u2")).thenReturn(Optional.of(orgUser("u2", "m1", OrgRole.LEARNER)));
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orgHierarchyService.validateManagerAssignment(ORG, "m1", "u2"));

        assertTrue(ex.getMessage().contains("descendant"));
    }

    @Test
    void validateManagerAssignment_rejectsIndirectReportAsManager() {
        when(userRepository.findById("u3")).thenReturn(Optional.of(orgUser("u3", "m2", OrgRole.LEARNER)));
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());

        assertThrows(
                IllegalArgumentException.class,
                () -> orgHierarchyService.validateManagerAssignment(ORG, "m1", "u3"));
    }

    @Test
    void validateManagerAssignment_allowsManagerOutsideSubtree() {
        when(userRepository.findById("m1")).thenReturn(Optional.of(orgUser("m1", "owner", OrgRole.MANAGER)));
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());

        orgHierarchyService.validateManagerAssignment(ORG, "u4", "m1");
    }

    @Test
    void validateManagerAssignment_blankManagerClearsWithoutLookup() {
        orgHierarchyService.validateManagerAssignment(ORG, "u2", null);
        orgHierarchyService.validateManagerAssignment(ORG, "u2", "  ");
    }

    @Test
    void validateManagerAssignment_newUserSkipsCycleCheck() {
        when(userRepository.findById("m1")).thenReturn(Optional.of(orgUser("m1", "owner", OrgRole.MANAGER)));

        orgHierarchyService.validateManagerAssignment(ORG, null, "m1");
    }

    @Test
    void collectDescendants_terminatesOnPreExistingCycle() {
        List<User> cyclic = List.of(
                orgUser("c1", "c2", OrgRole.MANAGER),
                orgUser("c2", "c1", OrgRole.MANAGER));
        when(userRepository.findByOrganizationId(ORG)).thenReturn(cyclic);

        Set<String> visible = orgHierarchyService.getVisibleUserIds(orgUser("c1", "c2", OrgRole.MANAGER));

        assertEquals(Set.of("c1", "c2"), visible);
    }

    @Test
    void buildHierarchyTree_nestsReportsUnderTheirManager() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());

        List<OrgHierarchyNodeDTO> roots = orgHierarchyService.buildHierarchyTree(ORG);

        assertEquals(1, roots.size());
        OrgHierarchyNodeDTO owner = roots.get(0);
        assertEquals("owner", owner.getUserId());
        assertEquals(2, owner.getReports().size());

        OrgHierarchyNodeDTO manager = owner.getReports().stream()
                .filter(n -> n.getUserId().equals("m1"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, manager.getReports().size());

        OrgHierarchyNodeDTO lead = manager.getReports().stream()
                .filter(n -> n.getUserId().equals("m2"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, lead.getReports().size());
        assertEquals("u3", lead.getReports().get(0).getUserId());
    }

    @Test
    void buildHierarchyTree_defaultsMissingOrgRoleToLearner() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(List.of(
                User.builder().id("u1").name("U").organizationId(ORG).build()));

        List<OrgHierarchyNodeDTO> roots = orgHierarchyService.buildHierarchyTree(ORG);

        assertEquals(OrgRole.LEARNER, roots.get(0).getOrgRole());
    }

    @Test
    void buildHierarchyTree_emptyOrgReturnsNoRoots() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(List.of());

        assertTrue(orgHierarchyService.buildHierarchyTree(ORG).isEmpty());
    }

    @Test
    void buildHierarchyTree_rootedAtManagerExcludesPeersAndSuperiors() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());

        List<OrgHierarchyNodeDTO> roots = orgHierarchyService.buildHierarchyTree(ORG, "m1");

        assertEquals(1, roots.size());
        assertEquals("m1", roots.get(0).getUserId());

        Set<String> visible = new java.util.HashSet<>();
        collectIds(roots, visible);
        assertEquals(Set.of("m1", "m2", "u3", "u2"), visible);
        assertTrue(visible.stream().noneMatch(id -> id.equals("owner") || id.equals("u4")));
    }

    @Test
    void buildHierarchyTree_rootedAtLeafHasNoReports() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());

        List<OrgHierarchyNodeDTO> roots = orgHierarchyService.buildHierarchyTree(ORG, "u2");

        assertEquals(1, roots.size());
        assertEquals("u2", roots.get(0).getUserId());
        assertTrue(roots.get(0).getReports().isEmpty());
    }

    @Test
    void buildHierarchyTree_unknownRootReturnsNothing() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(orgTree());

        assertTrue(orgHierarchyService.buildHierarchyTree(ORG, "does-not-exist").isEmpty());
    }

    @Test
    void buildHierarchyTree_terminatesOnPreExistingCycle() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(List.of(
                orgUser("c1", "c2", OrgRole.MANAGER),
                orgUser("c2", "c1", OrgRole.MANAGER)));

        List<OrgHierarchyNodeDTO> roots = orgHierarchyService.buildHierarchyTree(ORG, "c1");

        assertEquals(1, roots.size());
        Set<String> visible = new java.util.HashSet<>();
        collectIds(roots, visible);
        assertEquals(Set.of("c1", "c2"), visible);
    }

    @Test
    void buildHierarchyTree_wholeOrgTerminatesOnPreExistingCycle() {
        when(userRepository.findByOrganizationId(ORG)).thenReturn(List.of(
                orgUser("owner", null, OrgRole.ORG_OWNER),
                orgUser("c1", "c2", OrgRole.MANAGER),
                orgUser("c2", "c1", OrgRole.MANAGER)));

        List<OrgHierarchyNodeDTO> roots = orgHierarchyService.buildHierarchyTree(ORG);

        assertEquals(1, roots.size());
        assertEquals("owner", roots.get(0).getUserId());
    }

    private void collectIds(List<OrgHierarchyNodeDTO> nodes, Set<String> into) {
        for (OrgHierarchyNodeDTO node : nodes) {
            into.add(node.getUserId());
            collectIds(node.getReports(), into);
        }
    }
}
