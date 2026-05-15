package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class OtpLoginRequestDTO {
    private String email;
    private String otp;
    private String verificationToken;
}
