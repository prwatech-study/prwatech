package com.prwatech.courses.dto;

import com.prwatech.courses.model.CourseDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CourseDetailsDto {

    private CourseDetails courseDetails;
    private CourseRatingDto courseRatingDto;
    private Boolean isWishListed;
    private String wishListId;
}
