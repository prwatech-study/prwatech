package com.prwatech.courses.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourseRatingDto {

  public CourseRatingDto(String courseId, Integer totalRating, Integer totalRatingCount) {
    this.courseId = courseId;
    this.totalRating = totalRating;
    this.totalRatingCount = totalRatingCount;
  }

  private String courseId;
  private Integer totalRating;
  private Integer totalRatingCount;
  private Map<Integer, Integer> ratingList;
  private List<String> ratingMassageList;
}
