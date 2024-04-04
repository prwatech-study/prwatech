package com.prwatech.courses.service;

import com.prwatech.common.dto.PaginationDto;
import com.prwatech.courses.dto.CertificateDetailsDto;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.CourseDetailsDto;
import com.prwatech.courses.dto.CourseDetailsProjection;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.dto.CourseReviewRequestDto;
import com.prwatech.courses.dto.ForumFilterListingDto;
import com.prwatech.courses.enums.CourseLevelCategory;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.CourseReview;
import com.prwatech.courses.model.CourseTrack;
import com.prwatech.courses.model.Pricing;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;

public interface CourseDetailService {

  List<CourseCardDto> getMostPopularCourses(String userId, String platform);

  CourseDetailsDto getCourseDescriptionById(String id, String userId);

  Pricing getPriceByCourseId(ObjectId courseId, CourseLevelCategory type);

  List<CourseCardDto> getSelfPlacedCourses(String userId, String platform);

  PaginationDto getFreeCourses(String userId, Integer pageNumber, Integer pageSize);

  PaginationDto getAllCoursesByCategory(String userId,
      CourseLevelCategory category, Integer pageNumber, Integer pageSize, String platform);

  List<ForumFilterListingDto> getCoursesTitleListing();

  Pricing getCoursePriceByIdAndCategory(ObjectId id, CourseLevelCategory category);

  CourseRatingDto getRatingOfCourse(String courseId);

  CourseReview rateACourse(CourseReviewRequestDto courseReviewRequestDto);

  Set<CourseCardDto> getAllUserEnrolledCourses(ObjectId userId);

  Set<CourseCardDto> getAllCompletedCourse(ObjectId userId);

  void updateCurrentItem(ObjectId userId, ObjectId courseId, Integer currentItem);

  Map<String, String>searchByName(String name);

  CertificateDetailsDto getCertificateDetails(String userId, String courseId);

  Boolean makeItFreeAndNonFree(String courseId, Boolean isFree);

  CourseTrack enrollAFreeCourse(String userId, String courseId);

  Boolean rateACourse(String userId, String courseId, Integer rating);

  }
