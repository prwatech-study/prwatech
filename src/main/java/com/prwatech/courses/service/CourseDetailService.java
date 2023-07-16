package com.prwatech.courses.service;

import com.prwatech.common.dto.PaginationDto;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.CourseDetailsDto;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.dto.CourseReviewRequestDto;
import com.prwatech.courses.dto.ForumFilterListingDto;
import com.prwatech.courses.enums.CourseLevelCategory;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.CourseReview;
import com.prwatech.courses.model.Pricing;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;

public interface CourseDetailService {

  List<CourseCardDto> getMostPopularCourses(String userId);

  CourseDetailsDto getCourseDescriptionById(String id, String userId);

  Pricing getPriceByCourseId(ObjectId courseId, String type);

  List<CourseCardDto> getSelfPlacedCourses(String userId);

  List<CourseCardDto> getFreeCourses(String userId);

  PaginationDto getAllCoursesByCategory(String userId,
      CourseLevelCategory category, Integer pageNumber, Integer pageSize);

  List<ForumFilterListingDto> getCoursesTitleListing();

  Pricing getCoursePriceByIdAndCategory(ObjectId id, CourseLevelCategory category);

  CourseRatingDto getRatingOfCourse(String courseId);

  CourseReview rateACourse(CourseReviewRequestDto courseReviewRequestDto);

  Set<CourseCardDto> getAllUserEnrolledCourses(ObjectId userId);

  }
