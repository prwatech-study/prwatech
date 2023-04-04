package com.prwatech.courses.service.impl;

import com.prwatech.common.Constants;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.CourseReview;
import com.prwatech.courses.model.Pricing;
import com.prwatech.courses.repository.CourseDetailRepository;
import com.prwatech.courses.repository.CourseDetailsRepositoryTemplate;
import com.prwatech.courses.repository.CoursePricingRepositoryTemplate;
import com.prwatech.courses.repository.CourseReviewRepositoryTemplate;
import com.prwatech.courses.service.CourseDetailService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@AllArgsConstructor
public class CourseDetailServiceImpl implements CourseDetailService {

  private final CourseDetailsRepositoryTemplate courseDetailsRepositoryTemplate;
  private final CourseReviewRepositoryTemplate courseReviewRepositoryTemplate;
  private final CoursePricingRepositoryTemplate coursePricingRepositoryTemplate;
  private final CourseDetailRepository courseDetailRepository;

  @Override
  public List<CourseCardDto> getMostPopularCourses() {

    List<CourseDetails> courseDetailList = courseDetailsRepositoryTemplate.getMostPopularCourse();
    List<CourseCardDto> courseCardDtoList = new ArrayList<>();
    for (CourseDetails courseDetail : courseDetailList) {

      CourseRatingDto courseRatingDto = getRatingOfCourse(courseDetail.getId());
      CourseCardDto courseCardDto = new CourseCardDto();

      courseCardDto.setCourseId(courseDetail.getId());
      courseCardDto.setTitle(courseDetail.getCourse_Title());
      courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
      courseCardDto.setImgUrl(courseDetail.getCourse_Image());
      courseCardDto.setRatingNumber(courseRatingDto.getTotalRating().doubleValue());
      courseCardDto.setPeopleRatingNumber(courseRatingDto.getTotalRating().longValue());
      courseCardDto.setLectureNumber(getPriceOfCourseByCourseId(courseCardDto.getCourseId()));
      courseCardDto.setCourseDurationHours(6);
      courseCardDto.setCourseDurationMinute(30);

      courseCardDtoList.add(courseCardDto);
    }
    return courseCardDtoList;
  }

  @Override
  public CourseDetails getCourseDescriptionById(String id) {
    return courseDetailRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("No course found by this id :"));
  }

  private CourseRatingDto getRatingOfCourse(String courseId) {
    List<CourseReview> courseReviewList =
        courseReviewRepositoryTemplate.getCourseReviewByCourseId(courseId);
    Integer totalRating =
        courseReviewList.stream()
            .map(CourseReview::getReview_Number)
            .collect(Collectors.toList())
            .stream()
            .mapToInt(Integer::intValue)
            .sum();
    return new CourseRatingDto(courseId, totalRating, courseReviewList.size());
  }

  private Integer getPriceOfCourseByCourseId(String courseId) {
    Optional<Pricing> pricingOptional =
        coursePricingRepositoryTemplate.getPricingOfCourseByCourseId(courseId);
    return (pricingOptional.isEmpty())
        ? Constants.DEFAULT_PRICING
        : pricingOptional.get().getActual_Price();
  }
}
