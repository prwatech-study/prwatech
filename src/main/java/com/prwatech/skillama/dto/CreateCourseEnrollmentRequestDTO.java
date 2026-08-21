package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class CreateCourseEnrollmentRequestDTO {
    private String courseId;
    /** Optional note from the learner shown to the reviewing admin. */
    private String note;
}
