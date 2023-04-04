package com.prwatech.courses.controller;

import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.service.CourseDetailService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("api/public")
public class CourseDetailsController {

  private final CourseDetailService courseDetailService;

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
  public List<CourseCardDto> getHomeListingMostPopularCourses() {
    return courseDetailService.getMostPopularCourses();
  }

  // TODO :: get all popular courses list paginated data.

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
  public void getHomeListingSelfPlacedCourses() {}

  // TODO :: get all self placed coursed list paginated data.

  // get free courses list limit 10.
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
  public void getHomeListingFreeCourses() {}

  // get free online courses list paginated data.
}
