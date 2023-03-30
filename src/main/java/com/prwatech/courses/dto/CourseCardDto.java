package com.prwatech.courses.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourseCardDto {

  private String courseId;
  private String title;
  private Boolean isImgPresent;
  private String imgUrl;
  private Double ratingNumber;
  private Long peopleRatingNumber;
  private Integer price;
  private Integer lectureNumber;
  private Integer courseDurationHours;
  private Integer courseDurationMinute;
}
