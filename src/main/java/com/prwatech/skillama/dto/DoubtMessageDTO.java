package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoubtMessageDTO {
    private String id;
    private String sender;
    private String content;
    private String nudgeType;
    private Boolean helpful;
    private LocalDateTime timestamp;
}
