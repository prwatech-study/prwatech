package com.prwatech.promotion.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "Testimonials")
public class Testimonials {

  @Id private String id;

  @Field(name = "Name")
  private String Name;

  @Field(name = "Photo_URL")
  private String Photo_URL;

  @Field(name = "Message")
  private String Message;

  @Field(name = "Created_On")
  @CreationTimestamp
  private LocalDateTime Created_On;
}
