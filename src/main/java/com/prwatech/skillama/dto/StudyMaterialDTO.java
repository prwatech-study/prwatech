package com.prwatech.skillama.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyMaterialDTO {
    private String id;
    private String courseId;
    private String title;
    private String description;
    private String fileName;
    private String fileUrl;
    private String contentType;
    private Long fileSizeBytes;
    private Integer sortOrder;
    private LocalDateTime uploadedAt;
}
