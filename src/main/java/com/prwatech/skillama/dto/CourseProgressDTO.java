package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseProgressDTO {
    private String courseId;
    private Integer progress;
    private Integer totalLectures;
    private Integer completedLectures;
    private LocalDateTime lastAccessed;
    private List<LectureProgressDTO> lectures;
    private String message; // Optional, for update responses
}

