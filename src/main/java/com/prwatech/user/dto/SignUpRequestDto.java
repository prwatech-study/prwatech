package com.prwatech.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SignUpRequestDto {

    private String email;
    private String password;
    private Boolean isEmailLogin;
    private Long phoneNumber;
}
