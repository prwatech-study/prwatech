package com.prwatech.courses.controller;

import com.prwatech.common.dto.PaginationDto;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.CourseCurriCulamDto;
import com.prwatech.courses.dto.CourseDetailsDto;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.dto.CourseReviewRequestDto;
import com.prwatech.courses.dto.ForumFilterListingDto;
import com.prwatech.courses.enums.CourseLevelCategory;
import com.prwatech.courses.model.CourseCurriculam;
import com.prwatech.courses.model.CourseFAQs;
import com.prwatech.courses.model.CourseReview;
import com.prwatech.courses.model.Pricing;
import com.prwatech.courses.service.CourseCurriculamService;
import com.prwatech.courses.service.CourseDetailService;
import com.prwatech.courses.service.CourseFAQsService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/public")
public class CourseDetailsController {

  private final CourseDetailService courseDetailService;
  private final CourseCurriculamService courseCurriculamService;

  private final CourseFAQsService courseFAQsService;

  @ApiOperation(value = "Get most popular courses", notes = "Get most popular courses")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @GetMapping("/most-popular-course/listing/")
  @ResponseStatus(HttpStatus.OK)
  public List<CourseCardDto> getHomeListingMostPopularCourses(
          @RequestParam(value = "userId", required = false) String userId
  ) {
    return courseDetailService.getMostPopularCourses(userId);
  }

  @ApiOperation(
      value = "Get self placed courses on home page",
      notes = "Get self placed courses on home page")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @GetMapping("/self-placed-course/listing/")
  @ResponseStatus(HttpStatus.OK)
  public List<CourseCardDto> getHomeListingSelfPlacedCourses(
          @RequestParam(value = "userId", required = false) String userId
  ) {
    return courseDetailService.getSelfPlacedCourses(userId);
  }

  @ApiOperation(value = "Get free courses on home page", notes = "Get free courses on home page")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @GetMapping("/free-course/listing/")
  @ResponseStatus(HttpStatus.OK)
  public List<CourseCardDto> getHomeListingFreeCourses(
          @RequestParam(value = "userId", required = false) String userId
  ) {
    return courseDetailService.getFreeCourses(userId);
  }

  @ApiOperation(
      value = "Get course details by course id",
      notes = "Get Get course details by course id")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @GetMapping("/course-details/{courseId}")
  @ResponseStatus(HttpStatus.OK)
  public CourseDetailsDto getCourseDetailsByCourseId(
      @PathVariable(value = "courseId") @NotNull String courseId,
      @RequestParam(value = "userId", required = false) String userId) {
    return courseDetailService.getCourseDescriptionById(courseId, userId);
  }

  @ApiOperation(value = "Get course price by course id", notes = "Get course price by course id")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @GetMapping("/pricing/{courseId}")
  @ResponseStatus(HttpStatus.OK)
  public Pricing getPricingByCourseId(
      @PathVariable(value = "courseId") @NotNull ObjectId courseId,
      @RequestParam(value = "type") CourseLevelCategory category
      ) {
    return courseDetailService.getCoursePriceByIdAndCategory(courseId, category);
  }

  @ApiOperation(value = "Get all course listing by type", notes = "Get all course listing by type")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @GetMapping("/course/listing/all")
  @ResponseStatus(HttpStatus.OK)
  public PaginationDto getAllCoursesByType(
          @RequestParam(value = "userId", required = false) String userId,
      @RequestParam(value = "type") @NotNull CourseLevelCategory courseLevelCategory,
      @RequestParam(value = "pageNumber", defaultValue = "0") Integer pageNumber,
      @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
    return courseDetailService.getAllCoursesByCategory(userId, courseLevelCategory, pageNumber, pageSize);
  }

  @ApiOperation(
      value = "Get course curriculum by course id",
      notes = "Get course curriculum by course id")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @GetMapping("/course/{courseId}/curriculum")
  @ResponseStatus(HttpStatus.OK)
  public CourseCurriCulamDto getCourseCurriculumByCourseId(
      @PathVariable(value = "courseId") @NotNull String courseId,
      @RequestParam(value = "userId", required = false) String userId
      ) {
    return courseCurriculamService.getAllCurriculambyCourseId(new ObjectId(courseId), userId);
  }

  @ApiOperation(value = "Get course FAQ by course id", notes = "Get course FAQ by course id")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @GetMapping("/course/{courseId}/FAQs")
  @ResponseStatus(HttpStatus.OK)
  public List<CourseFAQs> getCourseFAQByCourseId(
      @PathVariable(value = "courseId") @NotNull String courseId) {
    return courseFAQsService.getAllCourseFAQsByCourseId(new ObjectId(courseId));
  }

  @ApiOperation(
      value = "Get course title listing for filter",
      notes = "Get course title listing for filter")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @GetMapping("/course/title/listing")
  @ResponseStatus(HttpStatus.OK)
  public List<ForumFilterListingDto> getCourseTitleListingForFilter() {
    return courseDetailService.getCoursesTitleListing();
  }


  @ApiOperation(
          value = "Get course rating by course Id",
          notes = "Get course rating by course Id")
  @ApiResponses(
          value = {
                  @ApiResponse(code = 200, message = "Success"),
                  @ApiResponse(code = 400, message = "Not Available"),
                  @ApiResponse(code = 401, message = "UnAuthorized"),
                  @ApiResponse(code = 403, message = "Access Forbidden"),
                  @ApiResponse(code = 404, message = "Not found"),
                  @ApiResponse(code = 422, message = "UnProcessable entity"),
                  @ApiResponse(code = 500, message = "Internal server error"),
          })
  @GetMapping("/course/rating/{id}")
  @ResponseStatus(HttpStatus.OK)
  public CourseRatingDto getCourseRatingById(
          @PathVariable(value = "id") String id
  ) {
    return courseDetailService.getRatingOfCourse(id);
  }

  @ApiOperation(value = "Rate a course by user", notes = "Rate a course by user")
  @ApiResponses(
      value = {
        @ApiResponse(code = 200, message = "Success"),
        @ApiResponse(code = 400, message = "Not Available"),
        @ApiResponse(code = 401, message = "UnAuthorized"),
        @ApiResponse(code = 403, message = "Access Forbidden"),
        @ApiResponse(code = 404, message = "Not found"),
        @ApiResponse(code = 422, message = "UnProcessable entity"),
        @ApiResponse(code = 500, message = "Internal server error"),
      })
  @PostMapping("/course/rate")
  @ResponseStatus(HttpStatus.OK)
  public CourseReview rateACourse(
          @RequestBody @NotNull CourseReviewRequestDto courseReviewRequestDto
          ) {
     return courseDetailService.rateACourse(courseReviewRequestDto);
  }
}
