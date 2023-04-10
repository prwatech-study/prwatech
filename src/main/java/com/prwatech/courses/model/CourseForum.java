package com.prwatech.courses.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "CourseForum")
public class CourseForum {

  @Id private String id;

  @Field(value = "Question")
  private String question;

  @Field(value = "Conversation")
  private List<Conversation> conversation;

  @Field(value = "LastUpdated")
  private LocalDateTime lastUpdated;

  @Field(value = "Question_By_Name")
  private String questionBy;
}
