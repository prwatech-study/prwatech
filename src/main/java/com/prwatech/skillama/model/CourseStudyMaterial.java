package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "course_study_materials")
public class CourseStudyMaterial {
    @Id
    private String id;

    @Indexed
    private String courseId;

    private String title;
    private String description;
    private String fileName;
    private String fileUrl;
    private String s3Key;
    private String contentType;
    private Long fileSizeBytes;
    private Integer sortOrder;

    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
