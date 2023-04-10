package com.prwatech.courses.controller;

import com.prwatech.courses.dto.ForumDto;
import com.prwatech.courses.service.ForumService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/public")
@AllArgsConstructor
public class ForumController {

  private final ForumService forumService;

  @ApiOperation(value = "Get all forums", notes = "Get all forums")
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
  @ResponseStatus(HttpStatus.OK)
  @GetMapping(value = "/forums")
  public List<ForumDto> getForumsList(
      @RequestParam(value = "courseId", required = false) String courseId) {
    return forumService.getForumList(courseId);
  }
}
