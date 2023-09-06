package com.prwatech.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserWalletResponseDto {

  private String id;
  private String User_Id;
  private Integer walletAmount;
  private String ReferalCode;
}
