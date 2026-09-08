package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CreateOrgDepartmentRequestDTO;
import com.prwatech.skillama.dto.OrgDepartmentDTO;
import com.prwatech.skillama.dto.OrgDepartmentsResponseDTO;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationDepartment;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.OrganizationDepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgDepartmentServiceTest {

    private static final String ORG_ID = "org-1";

    @Mock private OrganizationDepartmentRepository departmentRepository;
    @Mock private TenantSecurityService tenantSecurityService;
    @Mock private OrgNotificationService orgNotificationService;

    private OrgPermissionService orgPermissionService;
    private OrgDepartmentService service;

    @BeforeEach
    void setUp() {
        orgPermissionService = new OrgPermissionService(null);
        service = new OrgDepartmentService(
                departmentRepository, tenantSecurityService, orgNotificationService, orgPermissionService);
        Organization org = Organization.builder().id(ORG_ID).slug("acme").build();
        when(tenantSecurityService.requireActiveOrganization(ORG_ID)).thenReturn(org);
        when(departmentRepository.findByOrganizationIdOrderByNameAsc(ORG_ID)).thenReturn(List.of());
        when(departmentRepository.save(any(OrganizationDepartment.class))).thenAnswer(inv -> {
            OrganizationDepartment row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId("dept-1");
            }
            return row;
        });
    }

    private User owner() {
        return User.builder().id("owner-1").organizationId(ORG_ID).orgRole(OrgRole.ORG_OWNER)
                .role(User.UserRole.USER).build();
    }

    private User admin() {
        return User.builder().id("admin-1").organizationId(ORG_ID).orgRole(OrgRole.ORG_ADMIN)
                .role(User.UserRole.USER)
                .orgModulePermissions(new java.util.ArrayList<>(OrgPermissionService.noAccessUntilGranted()))
                .build();
    }

    @Test
    void ownerCanCreateCustomDepartment() {
        CreateOrgDepartmentRequestDTO request = new CreateOrgDepartmentRequestDTO();
        request.setCode("Naukri-Tech");
        request.setName("Naukri Tech");

        OrgDepartmentDTO created = service.createCustom(owner(), request);

        assertEquals("naukri-tech", created.getCode());
        assertEquals("Naukri Tech", created.getName());
        assertFalse(created.isFromCatalog());
        ArgumentCaptor<OrganizationDepartment> captor = ArgumentCaptor.forClass(OrganizationDepartment.class);
        verify(departmentRepository).save(captor.capture());
        assertEquals("naukri-tech", captor.getValue().getCode());
    }

    @Test
    void ownerCanCreateSubsidiaryTeamCodes() {
        CreateOrgDepartmentRequestDTO request = new CreateOrgDepartmentRequestDTO();
        request.setCode("99-tech");
        request.setName("99 Acres Tech");

        OrgDepartmentDTO created = service.createCustom(owner(), request);

        assertEquals("99-tech", created.getCode());
        assertEquals("99 Acres Tech", created.getName());
    }

    @Test
    void adminCannotCreateDepartments() {
        CreateOrgDepartmentRequestDTO request = new CreateOrgDepartmentRequestDTO();
        request.setCode("naukri-tech");
        request.setName("Naukri Tech");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.createCustom(admin(), request));
        assertEquals("Insufficient permission for DEPARTMENTS (CREATE)", ex.getMessage());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void ownerCanEnableCatalogDefault() {
        OrgDepartmentDTO enabled = service.enableCatalog(owner(), "engineering");
        assertEquals("engineering", enabled.getCode());
        assertEquals("Engineering", enabled.getName());
        assertTrue(enabled.isFromCatalog());
    }

    @Test
    void listMarksCatalogItemsEnabled() {
        OrganizationDepartment row = OrganizationDepartment.builder()
                .id("d1")
                .organizationId(ORG_ID)
                .code("engineering")
                .name("Engineering")
                .catalogCode("engineering")
                .fromCatalog(true)
                .active(true)
                .build();
        when(departmentRepository.findByOrganizationIdOrderByNameAsc(ORG_ID)).thenReturn(List.of(row));

        OrgDepartmentsResponseDTO listed = service.list(User.builder()
                .id("admin-legacy")
                .organizationId(ORG_ID)
                .orgRole(OrgRole.ORG_ADMIN)
                .role(User.UserRole.USER)
                .build());
        assertEquals(1, listed.getDepartments().size());
        assertTrue(listed.getCatalog().stream()
                .anyMatch(item -> "engineering".equals(item.getCode()) && item.isEnabled()));
        assertTrue(listed.getCatalog().stream()
                .anyMatch(item -> "sales".equals(item.getCode()) && !item.isEnabled()));
    }

    @Test
    void normalizeAssignmentMapsNameToCode() {
        OrganizationDepartment row = OrganizationDepartment.builder()
                .code("naukri-tech")
                .name("Naukri Tech")
                .active(true)
                .build();
        when(departmentRepository.findByOrganizationIdOrderByNameAsc(ORG_ID)).thenReturn(List.of(row));

        assertEquals("naukri-tech", service.normalizeAssignment(ORG_ID, "Naukri Tech"));
        assertEquals("naukri-tech", service.normalizeAssignment(ORG_ID, "naukri-tech"));
        assertEquals("legacy-sales", service.normalizeAssignment(ORG_ID, "legacy-sales"));
    }

    @Test
    void rejectsInvalidCodes() {
        CreateOrgDepartmentRequestDTO request = new CreateOrgDepartmentRequestDTO();
        request.setCode("Naukri Tech");
        request.setName("Naukri Tech");
        assertThrows(IllegalArgumentException.class, () -> service.createCustom(owner(), request));
    }
}
