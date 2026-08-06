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
}
