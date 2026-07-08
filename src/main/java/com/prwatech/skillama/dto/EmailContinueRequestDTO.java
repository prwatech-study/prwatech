package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class EmailContinueRequestDTO {
    private String email;
    private String password;
    private String verificationToken;
}
