package com.prwatech.courses.dto;

import com.prwatech.courses.model.Conversation;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ForumDto {

  private String id;
  private String question;
  private List<Conversation> conversation;
  private LocalDateTime lastUpdated;
  private String questionBy;
}
