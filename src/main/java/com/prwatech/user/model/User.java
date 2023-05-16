package com.prwatech.user.model;

import com.prwatech.user.enumuration.UserGender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "Users")
public class User {

  @Id private String id;

  @Field(name = "Name")
  private String Name;

  @Field(name = "UserName")
  private String UserName;

  @Field(name = "Email")
  private String Email;

  @Field(name = "Password")
  private String Password;

  @Field(name = "Phone_Number")
  private Long PhoneNumber;

  @Enumerated(EnumType.STRING)
  @Field(name = "gender")
  private UserGender gender;

  @Field(value = "date_of_birth")
  private LocalDateTime dateOfBirth;

  @Field(name = "is_profile_image_uploaded")
  private Boolean isProfileImageUploaded;

  @Field(name = "is_password_reset")
  private Boolean isPasswordReset = Boolean.FALSE;

  @Field(name = "is_mobile_registered")
  private Boolean isMobileRegistered = Boolean.FALSE;

  @Field(name = "is_google_signed_in")
  private Boolean isGoogleSignedIn;

  @Field(name = "profileImage")
  private String profileImage;

  @Field(name = "Qualification")
  private String Qualification;

  @Field(name = "Address")
  private String Address;

  @Field(name = "Referal_Code")
  private String Referal_Code;

  @Field(name = "Referer_Code")
  private String Referer_Code;

  @Field(name = "Refered_Users")
  @DBRef(lazy = true)
  private List<User> Refered_Users = new ArrayList<>();

  @Field(name = "is_resume_uploaded")
  private Boolean isResumeUploaded;

  @Field(name = "Resume_URL")
  private String Resume_URL;

  @CreationTimestamp
  @Field(name = "Created_On")
  private LocalDate Created_On;

  @Field(name = "Verified")
  private Boolean Verified;

  @Field(name = "Disable")
  private Boolean Disable;

  @Field(name = "last_login")
  private LocalDateTime lastLogin;
}
