package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class MigrateFreemiumConfirmDTO {
    private String email;
    private String otp;
    private String verificationToken;
    /** Required if the account has no phone on file yet */
    private String phone;
}
