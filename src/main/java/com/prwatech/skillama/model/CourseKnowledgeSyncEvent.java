package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "course_knowledge_sync_events")
public class CourseKnowledgeSyncEvent {

    public enum Status {
        STARTED,
        COMPLETE,
        FAILED
    }

    @Id
    private String id;

    @Indexed
    private String courseId;

    private String ingestionJobId;
    private String triggeredBy;
    private LocalDateTime triggeredAt;
    private Status status;
    private Integer fileCountAtSync;
    private Double estCostUsd;
    private Map<String, Object> statistics;
}
