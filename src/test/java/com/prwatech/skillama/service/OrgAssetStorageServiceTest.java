package com.prwatech.skillama.service;

import com.prwatech.skillama.model.OrgAssetKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrgAssetStorageServiceTest {

    @Mock private S3Client s3Client;

    @Test
    void sharedBucketStoresUnderOrgPrefix() {
        OrgAssetStorageService storage = shared();
        assertEquals("presentation-image-courses", storage.bucketNameFor("acme"));
        assertEquals("org/acme/branding/logo.svg", storage.objectKey("acme", OrgAssetKind.LOGO, ".svg"));
    }

    @Test
    void sharedUploadDoesNotCreateABucket() throws Exception {
        OrgAssetStorageService storage = shared();
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.svg", "image/svg+xml", "<svg/>".getBytes());
        OrgAssetStorageService.UploadedObject uploaded = storage.upload("acme", OrgAssetKind.LOGO, file);

        assertEquals("presentation-image-courses", uploaded.bucket());
        assertEquals("org/acme/branding/logo.svg", uploaded.key());
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void dedicatedModeSanitizesSlugForBucketName() {
        OrgAssetStorageService storage = dedicated();
        assertEquals("skillama-org-acme-corp", storage.bucketNameFor("Acme Corp"));
        assertEquals("skillama-org-naukri", storage.bucketNameFor("naukri"));
        assertEquals("branding/logo.png", storage.objectKey("naukri", OrgAssetKind.LOGO, ".png"));
    }

    @Test
    void rejectsBlankSlug() {
        OrgAssetStorageService storage = shared();
        assertThrows(IllegalArgumentException.class, () -> storage.objectKey("   ", OrgAssetKind.LOGO, ".png"));
    }

    private OrgAssetStorageService shared() {
        return new OrgAssetStorageService(
                s3Client, "ap-south-1", "presentation-image-courses", false, "skillama-org-", false);
    }

    private OrgAssetStorageService dedicated() {
        return new OrgAssetStorageService(
                s3Client, "ap-south-1", "presentation-image-courses", true, "skillama-org-", true);
    }
}
