package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class ConsumeQueryRequestDTO {
    private String queryType; // CHAT, DEBUG
    private String courseId;
}
