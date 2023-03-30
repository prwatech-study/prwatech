package com.prwatech.courses.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourseRatingDto {

  private String courseId;
  private Integer totalRating;
  private Integer totalRatingCount;
}
