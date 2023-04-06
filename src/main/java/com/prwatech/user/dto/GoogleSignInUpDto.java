package com.prwatech.user.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GoogleSignInUpDto {
  @NotNull(message = "Email can not be null")
  @NotEmpty(message = "Email can not be empty.")
  private String email;

  private String name;
  private String imageUrl;
}
