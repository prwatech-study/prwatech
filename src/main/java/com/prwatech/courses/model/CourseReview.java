package com.prwatech.courses.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "CourseReview")
public class CourseReview {

  @Id private String id;

  @Field(name = "Course_Id")
  private ObjectId Course_Id;

  @Field(name = "Reviewer_Id")
  private ObjectId Reviewer_Id;

  @Field(name = "Review_Message")
  private String Review_Message;

  @Field(name = "Review_Number")
  private Integer Review_Number;

  @Field(name = "Review_Status")
  private Boolean Review_Status;

  @Field(name = "Review_Course_Type")
  private String Review_Course_Type;

  @Field(name = "Trainer_Id")
  private ObjectId Trainer_Id;

  @Field(name = "Review_From")
  private String Review_From;
}
