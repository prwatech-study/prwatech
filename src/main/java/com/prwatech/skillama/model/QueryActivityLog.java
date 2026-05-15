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
@Document(collection = "query_activity_logs")
public class QueryActivityLog {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String queryType; // CHAT, DEBUG
    private String courseId;
    private LocalDateTime createdAt;
}
