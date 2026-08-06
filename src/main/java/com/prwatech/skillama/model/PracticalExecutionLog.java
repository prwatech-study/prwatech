package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Audit trail for one AI-generated-code execution against a practical dataset. Schema only for
 * now — populated once the sandboxed execution endpoint (ai-tutor + Lambda) is built; see the
 * "Secure CSV Execution Environment" design proposal.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "practical_execution_logs")
public class PracticalExecutionLog {
    @Id
    private String id;

    @Indexed
    private String userId;
    private String courseId;
    @Indexed
    private String datasetId;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String generatedCode;
    private String status;       // ok | rejected | error | timeout
    private String errorDetail;
    private Double cpuMs;
    private Double memoryMb;      // parsed from the Lambda REPORT log line
}
