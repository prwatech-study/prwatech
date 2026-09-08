package com.prwatech.skillama.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class OrgAssetStorageServiceTest {

    @Mock private S3Client s3Client;

    @Test
    void sanitizesSlugForBucketName() {
        OrgAssetStorageService storage =
                new OrgAssetStorageService(s3Client, "ap-south-1", "skillama-org-", true);
        assertEquals("skillama-org-acme-corp", storage.bucketNameFor("Acme Corp"));
        assertEquals("skillama-org-naukri", storage.bucketNameFor("naukri"));
    }

    @Test
    void rejectsBlankSlug() {
        OrgAssetStorageService storage =
                new OrgAssetStorageService(s3Client, "ap-south-1", "skillama-org-", true);
        assertThrows(IllegalArgumentException.class, () -> storage.bucketNameFor("   "));
    }
}
