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
    private String videoUrl;
    private String contentType;
    private Long fileSizeBytes;
    private String originalFileName;
    private LocalDateTime updatedAt;
}
