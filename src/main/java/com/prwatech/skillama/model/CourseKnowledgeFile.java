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
@Document(collection = "course_knowledge_files")
public class CourseKnowledgeFile {
    @Id
    private String id;

    @Indexed
    private String courseId;

    private String fileName;
    private String s3Key;
    private String contentType;
    private Long size;
    private Long estimatedTokens;

    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime deletedAt;
}
