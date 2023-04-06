package com.prwatech.courses.model;

import java.util.List;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "CourseFAQs")
public class CourseFAQs {

  @Id private String id;

  @Field(value = "Course_Id")
  @DBRef
  private ObjectId Course_Id;

  @Field(value = "FAQs")
  private List<Object> FAQs;
}
