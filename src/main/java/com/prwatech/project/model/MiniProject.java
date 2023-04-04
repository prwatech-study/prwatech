package com.prwatech.project.model;

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
@Document(value = "MiniProject")
public class MiniProject {

  @Id private String id;

  @Field(value = "Project_Title")
  private String projectTitle;

  @Field(value = "Project_Description")
  private String projectDescription;

  @Field(value = "Project_Banner")
  private String projectBanner;

  @Field(value = "Project_Video")
  private String projectVideo;
}
