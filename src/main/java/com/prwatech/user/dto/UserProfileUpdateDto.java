package com.prwatech.user.dto;

import com.prwatech.user.enumuration.UserGender;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileUpdateDto {
  private Long phoneNumber;
  private String email;
  private UserGender gender;
  private LocalDateTime dateOfBirth;
  private EducationUpdateDto educationUpdateDto;
}
