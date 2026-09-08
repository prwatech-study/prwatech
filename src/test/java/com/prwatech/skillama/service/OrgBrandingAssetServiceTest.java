package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.OrgBrandingAssetDTO;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.OrgAssetKind;
import com.prwatech.skillama.model.OrgModule;
import com.prwatech.skillama.model.OrgRole;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationAsset;
import com.prwatech.skillama.model.OrganizationBranding;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.OrganizationAssetRepository;
import com.prwatech.skillama.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgBrandingAssetServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationAssetRepository assetRepository;
    @Mock private OrgPermissionService orgPermissionService;
    @Mock private OrgNotificationService orgNotificationService;
    @Mock private S3Client s3Client;

    private OrgBrandingAssetService service;
    private OrgAssetStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new OrgAssetStorageService(s3Client, "ap-south-1", "skillama-org-", true);
        service = new OrgBrandingAssetService(
                organizationRepository, assetRepository, storage, orgPermissionService, orgNotificationService);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.save(any(OrganizationAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("missing").build());
    }

    @Test
    void bucketNameUsesOrgSlug() {
        assertEquals("skillama-org-acme", storage.bucketNameFor("acme"));
    }

    @Test
    void uploadWritesLogoUrlAndAssetRow() throws Exception {
        Organization org = Organization.builder()
                .id("org-1")
                .slug("acme")
                .branding(new OrganizationBranding())
                .build();
        User owner = User.builder()
                .id("u1")
                .organizationId("org-1")
                .orgRole(OrgRole.ORG_OWNER)
                .build();
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(org));
        when(assetRepository.findFirstByOrganizationIdAndKindAndActiveTrue("org-1", OrgAssetKind.LOGO))
                .thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", new byte[] {1, 2, 3, 4});

        OrgBrandingAssetDTO dto = service.upload(owner, OrgAssetKind.LOGO, file);

        assertEquals("skillama-org-acme", dto.getS3Bucket());
        assertEquals("branding/logo.png", dto.getS3Key());
        assertTrue(dto.getUrl().contains("skillama-org-acme"));
        assertEquals(dto.getUrl(), org.getBranding().getLogoUrl());
        assertEquals("skillama-org-acme", org.getAssetBucket());
        verify(s3Client).createBucket(any(software.amazon.awssdk.services.s3.model.CreateBucketRequest.class));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));

        ArgumentCaptor<OrganizationAsset> captor = ArgumentCaptor.forClass(OrganizationAsset.class);
        verify(assetRepository).save(captor.capture());
        assertEquals(OrgAssetKind.LOGO, captor.getValue().getKind());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    void adminNeedsBrandingUpdate() {
        User admin = User.builder()
                .id("a1")
                .organizationId("org-1")
                .orgRole(OrgRole.ORG_ADMIN)
                .build();
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(
                Organization.builder().id("org-1").slug("acme").build()));
        when(orgPermissionService.hasPermission(admin, OrgModule.BRANDING, AdminPermissionAction.UPDATE))
                .thenReturn(false);
        doThrow(new IllegalStateException("Insufficient permission for BRANDING (UPDATE)"))
                .when(orgPermissionService).require(admin, OrgModule.BRANDING, AdminPermissionAction.UPDATE);

        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1});
        assertThrows(IllegalStateException.class, () -> service.upload(admin, OrgAssetKind.LOGO, file));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }
}
