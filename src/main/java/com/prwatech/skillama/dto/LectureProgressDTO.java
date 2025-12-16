package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LectureProgressDTO {
    private String lectureId;
    private String moduleName;
    private String lectureName;
    private Boolean completed;
    private LocalDateTime completedAt;
    private Integer timeSpent;
}

