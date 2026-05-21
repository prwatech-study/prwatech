package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class ResetPasswordRequestDTO {
    private String email;
    private String otp;
    private String verificationToken;
    private String newPassword;
}
