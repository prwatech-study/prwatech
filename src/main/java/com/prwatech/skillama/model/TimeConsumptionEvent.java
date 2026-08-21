package com.prwatech.skillama.model;

import com.prwatech.skillama.util.IndiaTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * One accepted active-time heartbeat for a time-based (B2B) seat — the charged
 * seconds after server-side clamping, tagged with the platform module the
 * learner was using. Powers "seat hours by module" reporting.
 */
@Data
@Document(collection = "time_consumption_events")
public class TimeConsumptionEvent {
    @Id
    private String id;

    @Indexed
    private String userId;

    /** Feature surface, e.g. ai_tutor / ai_mentor / ai_exam. */
    private String module;

    /** Seconds actually charged (post-clamping), not the client's claim. */
    private int seconds;

    @Indexed
    private LocalDateTime createdAt = IndiaTime.now();
}
