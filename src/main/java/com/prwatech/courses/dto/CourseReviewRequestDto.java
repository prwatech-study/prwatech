package com.prwatech.courses.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourseReviewRequestDto {

    private String userId;
    private String courseId;
    private Integer rateNumber;
    private String reviewMessage;
}
