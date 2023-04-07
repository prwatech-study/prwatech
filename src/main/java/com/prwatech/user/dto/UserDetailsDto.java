package com.prwatech.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailsDto {

  private String id;
  private String Name;
  private String UserName;
  private String Email;
  private Long PhoneNumber;
  private Boolean isProfileImageUploaded;
  private String profileImage;
}
