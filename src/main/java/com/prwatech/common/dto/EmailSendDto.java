package com.prwatech.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmailSendDto {

  private String senderEmailId;
  private String receiverEmailId;
  private String subject;
  private String textMessage;
}
