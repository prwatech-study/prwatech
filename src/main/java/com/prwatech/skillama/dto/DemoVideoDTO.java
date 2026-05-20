package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DemoVideoDTO {
    private boolean available;
    private String title;
    private String description;
    /**
     * {@code direct} — use {@link #videoUrl} in a &lt;video&gt; tag (S3 or .mp4/.webm URL).
     * {@code youtube} or {@code embed} — use {@link #videoUrl} as an &lt;iframe src&gt; (YouTube is normalized to /embed/…).
     */
    private String playbackType;
    private String videoUrl;
    private String contentType;
    private Long fileSizeBytes;
    private String originalFileName;
    private LocalDateTime updatedAt;
}
