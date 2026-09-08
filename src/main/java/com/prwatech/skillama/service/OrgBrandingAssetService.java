package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.OrgBrandingAssetDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
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
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrgBrandingAssetService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationAssetRepository assetRepository;
    private final OrgAssetStorageService storageService;
    private final OrgPermissionService orgPermissionService;
    private final OrgNotificationService orgNotificationService;

    public void provisionBucket(Organization org) {
        if (org == null || org.getSlug() == null) {
            return;
        }
        try {
            storageService.ensureBucket(org.getSlug());
            org.setAssetBucket(storageService.bucketNameFor(org.getSlug()));
            organizationRepository.save(org);
        } catch (RuntimeException e) {
            log.warn("Could not provision org asset bucket for {}: {}", org.getSlug(), e.getMessage());
        }
    }

    @Transactional
    public OrgBrandingAssetDTO upload(User actor, OrgAssetKind kind, MultipartFile file) throws IOException {
        if (kind == null) {
            throw new IllegalArgumentException("Asset kind is required");
        }
        Organization org = requireOrgActor(actor, AdminPermissionAction.UPDATE);
        OrgAssetStorageService.UploadedObject uploaded;
        try {
            String bucket = storageService.bucketNameFor(org.getSlug());
            org.setAssetBucket(bucket);

            Optional<OrganizationAsset> previous =
                    assetRepository.findFirstByOrganizationIdAndKindAndActiveTrue(org.getId(), kind);
            previous.ifPresent(existing -> {
                if (existing.getS3Bucket() != null && existing.getS3Key() != null) {
                    storageService.deleteObject(existing.getS3Bucket(), existing.getS3Key());
                }
                existing.setActive(false);
                assetRepository.save(existing);
            });

            uploaded = storageService.upload(org.getSlug(), kind, file);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Failed to store organization asset: " + e.getMessage(), e);
        }

        OrganizationBranding branding = org.getBranding() == null ? new OrganizationBranding() : org.getBranding();
        applyUrl(branding, kind, uploaded.url());
        org.setBranding(branding);
        org.setUpdatedAt(IndiaTime.now());
        organizationRepository.save(org);

        LocalDateTime now = IndiaTime.now();
        OrganizationAsset saved = assetRepository.save(OrganizationAsset.builder()
                .organizationId(org.getId())
                .slug(org.getSlug())
                .kind(kind)
                .s3Bucket(uploaded.bucket())
                .s3Key(uploaded.key())
                .url(uploaded.url())
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .active(true)
                .uploadedBy(actor.getId())
                .uploadedAt(now)
                .build());

        orgNotificationService.notify(org.getId(), "BRANDING_UPDATED",
                "Uploaded " + kind.objectBaseName(), null, actor);

        return OrgBrandingAssetDTO.builder()
                .kind(kind)
                .url(saved.getUrl())
                .s3Bucket(saved.getS3Bucket())
                .s3Key(saved.getS3Key())
                .fileName(saved.getFileName())
                .fileSize(saved.getFileSize())
                .contentType(saved.getContentType())
                .branding(branding)
                .build();
    }

    private Organization requireOrgActor(User actor, AdminPermissionAction action) {
        if (actor == null || actor.getOrganizationId() == null) {
            throw new IllegalStateException("Organization context required");
        }
        Organization org = organizationRepository.findById(actor.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        if (!TenantSecurityService.isPlatformStaff(actor)) {
            OrgRole role = actor.getOrgRole();
            if (role != OrgRole.ORG_OWNER && role != OrgRole.ORG_ADMIN) {
                throw new IllegalStateException("Insufficient organization permissions");
            }
            orgPermissionService.require(actor, OrgModule.BRANDING, action);
        }
        return org;
    }

    private static void applyUrl(OrganizationBranding branding, OrgAssetKind kind, String url) {
        switch (kind) {
            case LOGO -> branding.setLogoUrl(url);
            case FAVICON -> branding.setFaviconUrl(url);
            case LOGIN_BACKGROUND -> branding.setLoginBackgroundUrl(url);
            case CERTIFICATE_LOGO -> branding.setCertificateLogoUrl(url);
        }
    }
}
