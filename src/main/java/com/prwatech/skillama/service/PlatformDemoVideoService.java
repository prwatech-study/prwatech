package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.DemoVideoDTO;
import com.prwatech.skillama.model.PlatformDemoVideo;
import com.prwatech.skillama.repository.PlatformDemoVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PlatformDemoVideoService {

    private static final String DEMO_VIDEO_S3_PREFIX = "demo-video";

    private final PlatformDemoVideoRepository repository;
    private final FileStorageService fileStorageService;

    @Value("${file.upload.s3.demo-video-prefix:demo-video}")
    private String demoVideoPrefix;

    public DemoVideoDTO getPublicConfig() {
        return repository.findById(PlatformDemoVideo.SINGLETON_ID)
                .map(this::toDto)
                .orElse(DemoVideoDTO.builder().available(false).build());
    }

    public DemoVideoDTO upload(MultipartFile video, String title, String description, String adminUserId)
            throws IOException {
        String prefix = StringUtils.hasText(demoVideoPrefix) ? demoVideoPrefix : DEMO_VIDEO_S3_PREFIX;
        String videoUrl = fileStorageService.uploadVideoToS3(video, prefix);
        return saveConfig(
                videoUrl,
                video.getContentType(),
                video.getSize(),
                video.getOriginalFilename(),
                title,
                description,
                adminUserId);
    }

    public DemoVideoDTO saveFromUrl(String videoUrl, String title, String description, String adminUserId) {
        if (!StringUtils.hasText(videoUrl) || !videoUrl.trim().startsWith("http")) {
            throw new IllegalArgumentException("A valid http(s) video URL is required");
        }
        return saveConfig(videoUrl.trim(), null, null, null, title, description, adminUserId);
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
            if (StringUtils.hasText(config.getVideoUrl())) {
                try {
                    fileStorageService.deleteFile(config.getVideoUrl());
                } catch (IOException e) {
                    // Best-effort delete from S3; still remove DB record
                }
            }
            repository.delete(config);
        });
    }

    private DemoVideoDTO saveConfig(
            String videoUrl,
            String contentType,
            Long fileSizeBytes,
            String originalFileName,
            String title,
            String description,
            String adminUserId) {
        repository.findById(PlatformDemoVideo.SINGLETON_ID).ifPresent(existing -> {
            if (StringUtils.hasText(existing.getVideoUrl())
                    && !existing.getVideoUrl().equals(videoUrl)) {
                try {
                    fileStorageService.deleteFile(existing.getVideoUrl());
                } catch (IOException ignored) {
                    // continue
                }
            }
        });

        PlatformDemoVideo config = repository.findById(PlatformDemoVideo.SINGLETON_ID)
                .orElse(new PlatformDemoVideo());

        config.setId(PlatformDemoVideo.SINGLETON_ID);
        config.setTitle(StringUtils.hasText(title) ? title.trim() : "How to use Skillama");
        config.setDescription(description != null ? description.trim() : null);
        config.setVideoUrl(videoUrl);
        config.setS3Key(null);
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
