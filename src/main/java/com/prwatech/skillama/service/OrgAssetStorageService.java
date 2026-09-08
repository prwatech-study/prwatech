package com.prwatech.skillama.service;

import com.prwatech.skillama.model.OrgAssetKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Organization branding images. Production uses the existing public course-image bucket
 * ({@code presentation-image-courses}) under {@code org/{slug}/branding/…} so the API role
 * only needs PutObject — not s3:CreateBucket.
 *
 * Dedicated {@code skillama-org-{slug}} buckets remain available when
 * {@code aws.s3.org-assets.dedicated-buckets=true}.
 */
@Service
public class OrgAssetStorageService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "image/svg+xml", "image/x-icon", "image/vnd.microsoft.icon");

    private final S3Client s3Client;
    private final String region;
    private final String sharedBucket;
    private final boolean dedicatedBuckets;
    private final String bucketPrefix;
    private final boolean createBucket;

    public OrgAssetStorageService(
            S3Client s3Client,
            @Value("${aws.s3.region:ap-south-1}") String region,
            @Value("${aws.s3.org-assets.bucket-name:presentation-image-courses}") String sharedBucket,
            @Value("${aws.s3.org-assets.dedicated-buckets:false}") boolean dedicatedBuckets,
            @Value("${aws.s3.org-assets.bucket-prefix:skillama-org-}") String bucketPrefix,
            @Value("${aws.s3.org-assets.create-bucket:false}") boolean createBucket) {
        this.s3Client = s3Client;
        this.region = region;
        this.sharedBucket = sharedBucket == null ? "" : sharedBucket.trim();
        this.dedicatedBuckets = dedicatedBuckets;
        this.bucketPrefix = bucketPrefix == null ? "skillama-org-" : bucketPrefix;
        this.createBucket = createBucket;
    }

    public String bucketNameFor(String slug) {
        if (!dedicatedBuckets) {
            if (!StringUtils.hasText(sharedBucket)) {
                throw new IllegalStateException("aws.s3.org-assets.bucket-name is required");
            }
            return sharedBucket;
        }
        String cleaned = sanitizeSlug(slug);
        String name = (bucketPrefix + cleaned).toLowerCase(Locale.ROOT);
        if (name.length() > 63) {
            name = name.substring(0, 63);
            name = name.replaceAll("-+$", "");
        }
        if (name.length() < 3) {
            throw new IllegalArgumentException("Organization slug is too short for an S3 bucket name");
        }
        return name;
    }

    public String objectKey(String slug, OrgAssetKind kind, String extension) {
        String file = kind.objectBaseName() + extension;
        if (dedicatedBuckets) {
            return "branding/" + file;
        }
        return "org/" + sanitizeSlug(slug) + "/branding/" + file;
    }

    public void ensureBucket(String slug) {
        if (!dedicatedBuckets) {
            return;
        }
        String bucket = bucketNameFor(slug);
        if (bucketExists(bucket)) {
            return;
        }
        if (!createBucket) {
            throw new IllegalStateException(
                    "Organization asset bucket " + bucket + " does not exist and auto-create is disabled");
        }
        createPublicReadBucket(bucket);
    }

    public UploadedObject upload(String slug, OrgAssetKind kind, MultipartFile file) throws IOException {
        validate(file);
        ensureBucket(slug);
        String bucket = bucketNameFor(slug);
        String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
        String key = objectKey(slug, kind, extension);
        String contentType = file.getContentType() != null ? file.getContentType() : "image/png";
        try {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .cacheControl("public, max-age=3600")
                    .build();
            s3Client.putObject(put, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (S3Exception e) {
            throw new IOException("Failed to upload organization asset: " + e.getMessage(), e);
        }
        long version = System.currentTimeMillis();
        return new UploadedObject(bucket, key, publicUrl(bucket, key) + "?v=" + version);
    }

    public void deleteObject(String bucket, String key) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(key)) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception ignored) {
            // best-effort replace
        }
    }

    public String publicUrl(String bucket, String key) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of 5MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid file type. Use JPG, PNG, GIF, WebP, SVG, or ICO.");
        }
        String name = file.getOriginalFilename();
        if (StringUtils.hasText(name) && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (!Set.of("jpg", "jpeg", "png", "gif", "webp", "svg", "ico").contains(ext)) {
                throw new IllegalArgumentException(
                        "Invalid file extension. Use JPG, PNG, GIF, WebP, SVG, or ICO.");
            }
        }
    }

    static String sanitizeSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new IllegalArgumentException("Organization slug is required");
        }
        String cleaned = slug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(cleaned)) {
            throw new IllegalArgumentException("Organization slug is invalid");
        }
        return cleaned;
    }

    private boolean bucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private void createPublicReadBucket(String bucket) {
        try {
            CreateBucketRequest.Builder create = CreateBucketRequest.builder().bucket(bucket);
            if (region != null && !"us-east-1".equals(region)) {
                create.createBucketConfiguration(CreateBucketConfiguration.builder()
                        .locationConstraint(BucketLocationConstraint.fromValue(region))
                        .build());
            }
            s3Client.createBucket(create.build());
            s3Client.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                    .bucket(bucket)
                    .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                            .blockPublicAcls(false)
                            .ignorePublicAcls(false)
                            .blockPublicPolicy(false)
                            .restrictPublicBuckets(false)
                            .build())
                    .build());
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Sid": "PublicReadOrgBranding",
                        "Effect": "Allow",
                        "Principal": "*",
                        "Action": ["s3:GetObject"],
                        "Resource": "arn:aws:s3:::%s/*"
                      }]
                    }
                    """.formatted(bucket);
            s3Client.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucket).policy(policy).build());
        } catch (S3Exception e) {
            throw new IllegalStateException(
                    "Could not create organization asset bucket " + bucket + ": " + e.getMessage(), e);
        }
    }

    private static String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        if (contentType == null) {
            return ".png";
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.contains("jpeg") || type.contains("jpg")) return ".jpg";
        if (type.contains("gif")) return ".gif";
        if (type.contains("webp")) return ".webp";
        if (type.contains("svg")) return ".svg";
        if (type.contains("icon")) return ".ico";
        return ".png";
    }

    public record UploadedObject(String bucket, String key, String url) {}
}
