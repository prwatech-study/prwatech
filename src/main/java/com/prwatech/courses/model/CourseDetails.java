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
@Document(value = "CourseDetails")
public class CourseDetails {

  @Id private String id;

  @Field(name = "Course_Category")
  private String Course_Category;

  @Field(name = "Course_URL")
  private String Course_URL;

  @Field(name = "Course_Title")
  private String Course_Title;

  @Field(name = "Course_Instruction")
  private String Course_Instruction;

  @Field(name = "Course_Description")
  private String Course_Description;

  @Field(name = "Course_Image")
  private String Course_Image;

  @Field(name = "Course_Banner")
  private String Course_Banner;

  @Field(name = "Course_Video")
  private String Course_Video;

  @Field(name = "Course_Preview_Video")
  private String Course_Preview_Video;

  @Field(name = "Course_Short_Description")
  private String Course_Short_Description;

  @Field(name = "Course_Type")
  private List<String> Course_Types;

  @Field(name = "Course_Keywords")
  private List<String> Course_Keywords;

  @Field(name = "Course_Details")
  private List<String> Course_Details;

  @Field(name = "Course_Status")
  private Integer Course_Status;

  @Field(name = "Created_On")
  private LocalDateTime Created_On;

  @Field(name = "Company_Logos")
  private List<String> Company_Logos;

  @Field(name = "Certification_Content")
  private String Certification_Content;

  @Field(name = "isFree")
  private Boolean isFree;

  @Field(name = "Disable")
  private Boolean Disable = Boolean.FALSE;

  @Field(name = "Product_Id")
  private String Product_Id;

  @Field(name = "Product_Id_Available")
  private Boolean Product_Id_Available = Boolean.FALSE;

  @Field(name = "Product_Id_Available_Online")
  private Boolean Product_Id_Available_Online = Boolean.FALSE;

  @Field(name = "Product_Id_Available_Webinar")
  private Boolean Product_Id_Available_Webinar = Boolean.FALSE;

  @Field(name = "Product_Id_Available_Classroom")
  private Boolean Product_Id_Available_Classroom = Boolean.FALSE;
}
