package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class DemoLoginRequestDTO {
    /** One-time code emailed to the owner via POST /users/demo-login/otp/send. */
    private String otp;
}
