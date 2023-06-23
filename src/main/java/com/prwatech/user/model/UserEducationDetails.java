package com.prwatech.user.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "user_education_details")
public class UserEducationDetails {

  @Id private String id;

  @Field(value = "User_Id")
  private String User_Id;

  @Field(value = "institute_name")
  private String instituteName;

  @Field(value = "program_name")
  private String programName;

  @Field(value = "field_of_study")
  private String fieldOfStudy;

  @Field(value = "start_time")
  private LocalDateTime startTime;

  @Field(value = "end_time")
  private LocalDateTime endTime;

  @Field(value = "is_completed")
  private Boolean isCompleted;
}
