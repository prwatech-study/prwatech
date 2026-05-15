package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class FreemiumRegisterRequestDTO {
    private String name;
    private String email;
    private String phone;
    private String verificationToken;
    private String password;
    private String referralCode;
}
