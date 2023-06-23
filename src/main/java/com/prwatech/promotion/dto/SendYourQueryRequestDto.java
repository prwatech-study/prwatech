package com.prwatech.promotion.dto;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SendYourQueryRequestDto {

  @NotNull(message = "message can not be null")
  private String name;

  @NotNull(message = "mobile number can not be null")
  private Long mobileNumber;

  @NotNull(message = "email id can not be null")
  private String emailId;

  @NotNull(message = "Message can not be null")
  private String message;
}
