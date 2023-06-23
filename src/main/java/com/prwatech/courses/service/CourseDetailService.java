package com.prwatech.courses.service;

import com.prwatech.common.dto.PaginationDto;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.dto.ForumFilterListingDto;
import com.prwatech.courses.enums.CourseLevelCategory;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.Pricing;
import java.util.List;
import org.bson.types.ObjectId;

public interface CourseDetailService {

  List<CourseCardDto> getMostPopularCourses();

  CourseDetails getCourseDescriptionById(String id);

  Pricing getPriceByCourseId(ObjectId courseId, String type);

  List<CourseCardDto> getSelfPlacedCourses();

  List<CourseCardDto> getFreeCourses();

  PaginationDto getAllCoursesByCategory(
      CourseLevelCategory category, Integer pageNumber, Integer pageSize);

  List<ForumFilterListingDto> getCoursesTitleListing();

  Pricing getCoursePriceByIdAndCategory(ObjectId id, CourseLevelCategory category);

  public CourseRatingDto getRatingOfCourse(String courseId);

  }
