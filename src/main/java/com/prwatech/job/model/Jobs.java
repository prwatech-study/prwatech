package com.prwatech.job.model;

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
@Document(value = "JobListing")
public class Jobs {

  @Id private String id;

  @Field(value = "Category_Id")
  @DBRef
  private ObjectId CategoryId;

  @Field(value = "Job_Title")
  private String JobTitle;

  @Field(value = "Job_Description")
  private String JobDescription;

  @Field(value = "Link")
  private String Link;

  @Field(value = "Company_Name")
  private String CompanyName;

  @Field(value = "Experience")
  private String Experience;

  @Field(value = "Location")
  private String Location;

  @Field(value = "Enable")
  private Boolean Enable;

  @Field(value = "Created_On")
  private LocalDateTime CreatedOn;
}
