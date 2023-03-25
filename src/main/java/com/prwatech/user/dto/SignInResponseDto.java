package com.prwatech.user.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SignInResponseDto {
  private String accessToken;
  private String refreshToken;
  private LocalDateTime expiresIn;
  private LocalDateTime refreshTokenExpiresIn;
}
