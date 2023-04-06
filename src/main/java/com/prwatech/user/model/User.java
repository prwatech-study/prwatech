package com.prwatech.user.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

  @Field(name = "qualification")
  private String qualification;

  @Field(name = "address")
  private String address;

  @Field(name = "referalCode")
  private String referalCode;

  @Field(name = "refererCode")
  private String refererCode;

  @Field(name = "referedUser")
  @DBRef(lazy = true)
  private List<User> referedUsers = new ArrayList<>();

  @Field(name = "is_resume_uploaded")
  private Boolean isResumeUploaded;

  @Field(name = "resumeUrl")
  private String resumeUrl;

  @Field(name = "verified")
  private Boolean verified;

  @Field(name = "disable")
  private Boolean disable;

  @Field(name = "last_login")
  private LocalDateTime lastLogin;
}
