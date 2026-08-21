package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Singleton, admin-entered baseline used to turn AI usage volume into an
 * estimated time/cost saved figure. No historical human-instructor timing
 * data exists, so these are assumptions the admin configures, not measured
 * facts — every value derived from them must be labeled as an estimate.
 */
@Data
@Document(collection = "platform_efficiency_assumptions")
public class PlatformEfficiencyAssumptions {
    public static final String SINGLETON_ID = "PLATFORM_EFFICIENCY_ASSUMPTIONS";

    @Id
    private String id = SINGLETON_ID;
    private double assumedManualQuizCreationMinutes = 90.0;
    private double assumedManualExamCreationMinutes = 120.0;
    private double assumedManualDoubtResolutionMinutes = 10.0;
    private double assumedHourlyInstructorCostInr = 500.0;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
