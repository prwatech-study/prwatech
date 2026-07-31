package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class DoubtFeedbackRequestDTO {
    private String messageId;
    private Boolean helpful;
}
