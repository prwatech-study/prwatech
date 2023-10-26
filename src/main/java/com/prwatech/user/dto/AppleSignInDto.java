package com.prwatech.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppleSignInDto {
    private String email;
    private String name;
    @NotNull
    private String appleString;
    private String imgUrl;
}
