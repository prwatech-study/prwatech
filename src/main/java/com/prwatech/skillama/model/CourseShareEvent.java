package com.prwatech.skillama.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;

/** One row per (user, course, platform) — the unique index makes the credit reward idempotent. */
@Data
@Document(collection = "course_share_events")
@CompoundIndex(name = "user_course_platform_unique", def = "{'userId': 1, 'courseId': 1, 'platform': 1}", unique = true)
public class CourseShareEvent {
    @Id
    private String id;
    private String userId;
    private String courseId;
    /** INSTAGRAM, LINKEDIN — the only platforms course sharing currently supports. */
    private String platform;
    /** Reward (USD) actually applied at share time — owner-tunable, so history must not re-derive this. */
    private double rewardUsd;
    private LocalDateTime createdAt = IndiaTime.now();
}
