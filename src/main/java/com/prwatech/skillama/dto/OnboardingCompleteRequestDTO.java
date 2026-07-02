package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class OnboardingCompleteRequestDTO {
    private String name;
    private String phone;
    private String freemiumCourseId;
    private String referralCode;
}
