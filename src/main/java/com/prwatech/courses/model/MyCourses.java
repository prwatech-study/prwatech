package com.prwatech.courses.model;

import java.util.List;
import javax.persistence.Table;
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
@Table
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "MyCourses")
public class MyCourses {

  @Id private String id;

  @Field(value = "User_Id")
  @DBRef
  private ObjectId User_Id;

  @Field(value = "Course_Id")
  @DBRef
  private ObjectId Course_Id;

  @Field(value = "Course_Type")
  private String Course_Type;

  @Field(value = "Schedule_Id")
  @DBRef
  private ObjectId Schedule_Id;

  @Field(value = "Status")
  private List<String> Status;

  @Field(value = "Disabled")
  private Boolean Disabled;
}
