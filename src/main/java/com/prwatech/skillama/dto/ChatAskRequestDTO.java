package com.prwatech.skillama.dto;

import lombok.Data;

import java.util.List;

/** Asks a chat question — the backend generates the answer (see UserProfileService.askChat). */
@Data
public class ChatAskRequestDTO {
    private String query;
    private String topic;
    private String course;
    private List<String> prevTopicList;
    private String lastAiReply;
    private String courseId;
    private String lectureContext;
    /** "text" (default) or "audio" — the learner's original input modality, for analytics;
     *  the AI call itself is always text-based (see SkillamaAiClient#answerQuery). */
    private String questionType;
    /** Optional client-side learner memory summary (pace, weak topics, facts). */
    private String learnerContext;
    /** "explain" (default) or "socratic" — teaching style hint from the LMS chat toggle. */
    private String tutorMode;
    /**
     * Optional {@code en} or {@code hi} from the LMS Hindi toggle.
     * When {@code hi} and platform Hindi tutor is enabled, overrides profile locale for this ask.
     */
    private String preferredLanguage;
}
