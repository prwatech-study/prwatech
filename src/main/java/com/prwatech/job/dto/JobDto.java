package com.prwatech.job.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobDto {
  private String id;

  private String CategoryId;

  private String JobTitle;

  private String JobDescription;

  private String Link;

  private String CompanyName;

  private String Experience;

  private String Location;
}
