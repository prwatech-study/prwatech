package com.prwatech.skillama.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/** Platform-owned practice set. Not an org hiring schedule. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_mock_interview_configs")
public class AiMockInterviewConfig {
    @Id
    private String id;
    private String title;
    private String description;
    /** BEHAVIORAL, TECHNICAL, or MIXED. */
    private String style;
    private Integer durationMinutes;
    private String jdText;
    private String courseName;
    @Builder.Default
    private List<String> questions = new ArrayList<>();
    private boolean active;
    private Integer sortOrder;
}
