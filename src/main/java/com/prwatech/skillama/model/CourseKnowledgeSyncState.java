package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Per-course latest sync metadata (one document per course). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "course_knowledge_sync_state")
public class CourseKnowledgeSyncState {
    @Id
    private String id;

    @Indexed(unique = true)
    private String courseId;

    private String lastIngestionJobId;
    private String lastSyncStatus;
    private LocalDateTime lastSyncedAt;
    private Long kbVersion;
}
