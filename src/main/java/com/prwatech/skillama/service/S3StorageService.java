package com.prwatech.skillama.service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Shared AWS S3 uploads for Skillama curriculum images and platform demo video
 * (same bucket/credentials as configured for admin media).
 */
@Service
@Slf4j
public class S3StorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-msvideo"
    );

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;

    @Value("${skillama.aws.s3.bucket:}")
    private String bucket;

    @Value("${skillama.aws.s3.region:ap-south-1}")
    private String region;

    @Value("${skillama.aws.access-key:}")
    private String accessKey;

    @Value("${skillama.aws.secret-key:}")
    private String secretKey;

    @Value("${skillama.aws.s3.curriculum-prefix:curriculum/images/}")
    private String curriculumPrefix;

    @Value("${skillama.aws.s3.demo-video-prefix:demo-video/}")
    private String demoVideoPrefix;

    private AmazonS3 s3Client;

    @PostConstruct
    void initClient() {
        if (!isConfigured()) {
            log.warn(
                    "Skillama S3 not configured (skillama.aws.s3.bucket / access-key). Media upload disabled.");
            return;
        }
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region)
                .build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(bucket)
                && StringUtils.hasText(accessKey)
                && StringUtils.hasText(secretKey);
    }

    public UploadResult uploadCurriculumImage(MultipartFile file) throws IOException {
        return upload(file, curriculumPrefix, ALLOWED_IMAGE_TYPES, MAX_IMAGE_BYTES, "curriculum");
    }

    public UploadResult uploadDemoVideo(MultipartFile file) throws IOException {
        return upload(file, demoVideoPrefix, ALLOWED_VIDEO_TYPES, MAX_VIDEO_BYTES, "demo");
    }

    public void deleteObject(String s3Key) {
        if (!isConfigured() || !StringUtils.hasText(s3Key)) {
            return;
        }
        try {
            s3Client.deleteObject(bucket, s3Key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", s3Key, e.getMessage());
        }
    }

    public String buildPublicUrl(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
    }

    public String extractKeyFromUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int idx = url.indexOf(".amazonaws.com/");
        if (idx < 0) {
            return null;
        }
        return url.substring(idx + ".amazonaws.com/".length());
    }

    private UploadResult upload(
            MultipartFile file,
            String prefix,
            Set<String> allowedTypes,
            long maxBytes,
            String namePrefix) throws IOException {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "S3 is not configured. Set skillama.aws.s3.bucket, skillama.aws.access-key, and skillama.aws.secret-key.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File exceeds maximum allowed size");
        }

        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase()
                : "";
        if (!allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        String extension = extensionFromContentType(contentType, file.getOriginalFilename());
        String key = prefix + namePrefix + "-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 8) + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        PutObjectRequest request = new PutObjectRequest(bucket, key, file.getInputStream(), metadata);
        request.setCannedAcl(CannedAccessControlList.PublicRead);

        s3Client.putObject(request);

        UploadResult result = new UploadResult();
        result.setKey(key);
        result.setUrl(buildPublicUrl(key));
        result.setContentType(file.getContentType());
        result.setFileSizeBytes(file.getSize());
        result.setOriginalFileName(file.getOriginalFilename());
        return result;
    }

    private String extensionFromContentType(String contentType, String originalName) {
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf('.'));
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            case "video/x-msvideo" -> ".avi";
            default -> contentType.startsWith("video/") ? ".mp4" : ".jpg";
        };
    }

    @Getter
    @lombok.Setter
    public static class UploadResult {
        private String url;
        private String key;
        private String contentType;
        private Long fileSizeBytes;
        private String originalFileName;
    }
}
