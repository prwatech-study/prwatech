package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class AiAnswerFeedbackRequestDTO {
    private String messageId;
    private String courseId;
    /** AI surface the answer came from, e.g. chat_ask. Optional; defaults to chat_ask. */
    private String endpoint;
    private Boolean helpful;
}
