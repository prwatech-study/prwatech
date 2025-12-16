package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.UserCourseEnrollment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnrollUserRequest {
    private String courseId;
    private UserCourseEnrollment.EnrollmentType enrollmentType; // ASSIGNED or PURCHASED (optional, defaults to ASSIGNED)
}

