package com.prwatech.user.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EducationUpdateDto {

  private String instituteName;
  private String programName;
  private String fieldOfStudy;
  private LocalDate startTime;
  private LocalDate endTime;
}
