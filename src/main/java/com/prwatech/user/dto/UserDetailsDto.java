package com.prwatech.user.dto;

import java.time.LocalDateTime;
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
  private String gender;
  private LocalDateTime dateOfBirth;
  private String Qualification;
}
