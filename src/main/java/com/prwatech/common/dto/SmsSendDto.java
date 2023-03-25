package com.prwatech.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SmsSendDto {

  private String message;
  private String language;
  private String route;
  private int flash;
  private String numbers;
}
