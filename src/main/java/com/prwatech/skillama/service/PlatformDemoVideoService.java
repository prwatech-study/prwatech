package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.DemoVideoDTO;
import com.prwatech.skillama.model.PlatformDemoVideo;
import com.prwatech.skillama.repository.PlatformDemoVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PlatformDemoVideoService {

    private final PlatformDemoVideoRepository repository;
    private final S3StorageService s3StorageService;

    public DemoVideoDTO getPublicConfig() {
        return repository.findById(PlatformDemoVideo.SINGLETON_ID)
                .map(this::toDto)
                .orElse(DemoVideoDTO.builder().available(false).build());
    }

    /** Upload file to S3 (same service as curriculum images). */
    public DemoVideoDTO upload(MultipartFile video, String title, String description, String adminUserId)
            throws IOException {
        S3StorageService.UploadResult result = s3StorageService.uploadDemoVideo(video);
        return saveConfig(
                result.getUrl(),
                result.getKey(),
                result.getContentType(),
                result.getFileSizeBytes(),
                result.getOriginalFileName(),
                title,
                description,
                adminUserId);
    }

    /** Use an existing AWS / HTTPS URL (e.g. already uploaded to your bucket). */
    public DemoVideoDTO saveFromUrl(String videoUrl, String title, String description, String adminUserId) {
        if (!StringUtils.hasText(videoUrl) || !videoUrl.trim().startsWith("http")) {
            throw new IllegalArgumentException("A valid http(s) video URL is required");
        }
        String trimmedUrl = videoUrl.trim();
        String s3Key = s3StorageService.extractKeyFromUrl(trimmedUrl);
        return saveConfig(trimmedUrl, s3Key, null, null, null, title, description, adminUserId);
    }

    public DemoVideoDTO updateMetadata(String title, String description, String adminUserId) {
        PlatformDemoVideo config = repository.findById(PlatformDemoVideo.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("No demo video configured yet"));
        if (StringUtils.hasText(title)) {
            config.setTitle(title.trim());
        }
        if (description != null) {
            config.setDescription(description.trim());
        }
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(adminUserId);
        return toDto(repository.save(config));
    }

    public void remove(String adminUserId) {
        repository.findById(PlatformDemoVideo.SINGLETON_ID).ifPresent(config -> {
            if (StringUtils.hasText(config.getS3Key())) {
                s3StorageService.deleteObject(config.getS3Key());
            }
            repository.delete(config);
        });
    }

    private DemoVideoDTO saveConfig(
            String videoUrl,
            String s3Key,
            String contentType,
            Long fileSizeBytes,
            String originalFileName,
            String title,
            String description,
            String adminUserId) {
        repository.findById(PlatformDemoVideo.SINGLETON_ID).ifPresent(existing -> {
            if (StringUtils.hasText(existing.getS3Key())
                    && (s3Key == null || !existing.getS3Key().equals(s3Key))) {
                s3StorageService.deleteObject(existing.getS3Key());
            }
        });

        PlatformDemoVideo config = repository.findById(PlatformDemoVideo.SINGLETON_ID)
                .orElse(new PlatformDemoVideo());

        config.setId(PlatformDemoVideo.SINGLETON_ID);
        config.setTitle(StringUtils.hasText(title) ? title.trim() : "How to use Skillama");
        config.setDescription(description != null ? description.trim() : null);
        config.setVideoUrl(videoUrl);
        config.setS3Key(s3Key);
        config.setContentType(contentType);
        config.setFileSizeBytes(fileSizeBytes);
        config.setOriginalFileName(originalFileName);
        config.setEnabled(true);
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(adminUserId);

        return toDto(repository.save(config));
    }

    private DemoVideoDTO toDto(PlatformDemoVideo config) {
        boolean available = config.isEnabled() && StringUtils.hasText(config.getVideoUrl());
        return DemoVideoDTO.builder()
                .available(available)
                .title(config.getTitle())
                .description(config.getDescription())
                .videoUrl(available ? config.getVideoUrl() : null)
                .contentType(config.getContentType())
                .fileSizeBytes(config.getFileSizeBytes())
                .originalFileName(config.getOriginalFileName())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
