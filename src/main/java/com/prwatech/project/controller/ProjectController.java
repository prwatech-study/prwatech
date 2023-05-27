package com.prwatech.project.controller;

import com.prwatech.project.dto.ProjectDto;
import com.prwatech.project.service.ProjectService;
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
public class ProjectController {

  private final ProjectService projectService;

  @ApiOperation(value = "Get all project list", notes = "Get all project list")
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
  @GetMapping(value = "/projects")
  public List<ProjectDto> getProjectsList() {
    return projectService.getProjects();
  }

  @ApiOperation(
      value = "Get project description by project id",
      notes = "Get project description by project id")
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
  @GetMapping(value = "/project/description")
  public ProjectDto getProjectDescription(@RequestParam(value = "projectId") String projectId) {
    return projectService.getProjectDescription(projectId);
  }

  @ApiOperation(value = "Say hi to appliation", notes = "Say hi to appliation")
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
  @GetMapping(value = "/say/hi")
  public String sayingHiToApp() {
    return "Say hi App!";
  }
}
