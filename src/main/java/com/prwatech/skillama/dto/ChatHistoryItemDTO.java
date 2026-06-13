package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Lightweight chat row for paginated history reads. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryItemDTO {
    private String id;
    private String question;
    private String answer;
    private LocalDateTime timestamp;
    private String lectureContext;
    private String courseId;
}
