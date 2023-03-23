package com.prwatech.authentication.dto;

import com.prwatech.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Auth0SignUpDto {

    private String ClientId;
    private String email;
    private String password;
    private String connection;

}
