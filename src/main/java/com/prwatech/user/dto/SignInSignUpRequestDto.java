package com.prwatech.user.dto;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SignInSignUpRequestDto {

  private String email;
  @NotNull private Boolean isSignUp;
  private String password;
}
