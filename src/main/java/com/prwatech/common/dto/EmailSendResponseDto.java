package com.prwatech.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmailSendResponseDto {

  private String userId;
  private String receiverId;
  private String messageResponse;
  private HttpStatus httpStatus;
}
