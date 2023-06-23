package com.prwatech.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EducationUpdateDto {

  private String id;
  private String instituteName;
  private String programName;
  private String fieldOfStudy;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
}
