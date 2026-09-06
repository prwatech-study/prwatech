package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

@Value
@Builder
public class KnowledgeBaseFileDTO {
    String id;
    String courseId;
    String fileName;
    String s3Key;
    String contentType;
    Long size;
    Long estimatedTokens;
    String uploadedBy;
    LocalDateTime uploadedAt;
}
