package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Singleton platform config for the portal onboarding demo video (common for all users).
 */
@Data
@Document(collection = "platform_demo_video")
public class PlatformDemoVideo {
    public static final String SINGLETON_ID = "PLATFORM_DEMO_VIDEO";

    @Id
    private String id = SINGLETON_ID;
    private String title;
    private String description;
    private String videoUrl;
    /**
     * How clients should play {@link #videoUrl}: {@code direct} (file / &lt;video&gt;), {@code youtube}, or {@code embed} (iframe src).
     */
    private String playbackType;
    private String s3Key;
    private String contentType;
    private Long fileSizeBytes;
    private String originalFileName;
    private boolean enabled = true;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
