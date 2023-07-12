package com.prwatech.courses.dto;

import com.prwatech.courses.enums.CourseLevelCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourseCardDto {

  private String cartId;
  private String wishListId;
  private String courseId;
  private String title;
  private Boolean isImgPresent;
  private String imgUrl;
  private CourseRatingDto courseRatingDto;
  private Integer price;
  private Integer discountedPrice;
  private Integer lectureNumber;
  private Integer courseDurationHours;
  private Integer courseDurationMinute;
  private CourseLevelCategory courseLevelCategory;
}
