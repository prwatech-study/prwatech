package com.prwatech.courses.model;

import java.time.LocalDateTime;
import java.util.List;
import javax.persistence.Id;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prwatech.courses.dto.Course_Curriculam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "CourseCurriculam")
public class CourseCurriculam {

  @Id private String id;

  @Field(value = "Course_Id")
  @DBRef
  private ObjectId Course_Id;

  @Field(value = "Course_Detail_Id")
  @DBRef
  private ObjectId Course_Detail_Id;

  @Field(value = "Course_Curriculam")
  @JsonProperty(value = "Course_Curriculam")
  private List<com.prwatech.courses.dto.Course_Curriculam> Course_Curriculam;

  @Field(value = "Last_Updated")
  @UpdateTimestamp
  private LocalDateTime Last_Updated;
}
