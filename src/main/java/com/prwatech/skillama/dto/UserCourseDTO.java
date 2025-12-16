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
public class UserCourseDTO {
    private String id;
    private String name;
    private String description;
    private String thumbnail; // Optional: Course thumbnail image URL
    private Integer progress;
    private Integer totalLectures;
    private Integer completedLectures;
    private String status;
    private LocalDateTime enrolledAt;
    private LocalDateTime lastAccessed;
}

