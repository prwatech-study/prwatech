package com.prwatech.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlaneEmailSendDto {

  private String senderMailId;
  private String receiverMailId;
  private String subject;
  private String textBody;
}
