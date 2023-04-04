package com.prwatech.courses.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Conversation {
  private String User_Id;
  private String User_Type;
  private String Query;
  private LocalDateTime CreatedOn;
}
