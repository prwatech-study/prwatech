package com.prwatech.skillama.model;

import com.prwatech.skillama.util.IndiaTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * One thumbs-up/down rating a learner gave on a single AI answer. One row per
 * (user, messageId) — re-rating the same answer overwrites the previous vote.
 * Powers the "AI helpful rate" metric on the investor dashboard.
 */
@Data
@Document(collection = "ai_answer_feedback")
@CompoundIndex(name = "user_message_unique", def = "{'userId': 1, 'messageId': 1}", unique = true)
public class AiAnswerFeedback {
    @Id
    private String id;

    @Indexed
    private String userId;

    /** Client-side message identifier of the rated AI answer. */
    private String messageId;

    private String courseId;

    /** Which AI surface produced the answer, e.g. chat_ask / ai_mentor_ask. */
    private String endpoint;

    private boolean helpful;

    @Indexed
    private LocalDateTime createdAt = IndiaTime.now();

    private LocalDateTime updatedAt = IndiaTime.now();
}
