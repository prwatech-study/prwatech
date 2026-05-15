package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class OtpVerifyRequestDTO {
    private String email;
    private String otp;
}
