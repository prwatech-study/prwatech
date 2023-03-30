package com.prwatech.courses.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "Pricing")
public class Pricing {

  @Id private String id;

  @DBRef
  @Field(name = "Course_Id")
  private String Course_Id;

  @Field(name = "Course_Type")
  private String Course_Type;

  @Field(name = "Actual_Price")
  private Integer Actual_Price;

  @Field(name = "Discounted_Price")
  private Integer Discounted_Price;
}
