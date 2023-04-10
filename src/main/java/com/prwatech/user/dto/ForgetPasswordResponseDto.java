package com.prwatech.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ForgetPasswordResponseDto {

  private String userId;
  private String emailId;
  private String message;
  private Boolean isEmailSend;
}
