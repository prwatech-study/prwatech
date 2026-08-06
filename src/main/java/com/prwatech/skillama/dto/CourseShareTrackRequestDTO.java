package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class CourseShareTrackRequestDTO {
    private String courseId;
    /** INSTAGRAM, LINKEDIN */
    private String platform;
}
