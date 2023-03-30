package com.prwatech.job.controller;

import com.prwatech.common.Constants;
import com.prwatech.job.dto.JobDto;
import com.prwatech.job.service.JobService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@AllArgsConstructor
public class JobController {

  private final JobService jobService;

  @ApiOperation(value = "Get All Job Listing", notes = "Get All Job Listing")
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
  @ApiImplicitParams({
    @ApiImplicitParam(
        name = Constants.AUTH,
        value = Constants.TOKEN_TYPE,
        required = true,
        dataType = Constants.AUTH_DATA_TYPE,
        paramType = Constants.AUTH_PARAM_TYPE)
  })
  @GetMapping("/job/listing/")
  @ResponseStatus(HttpStatus.OK)
  public List<JobDto> jobsList() {

    return jobService.getJobListing();
  }

  @ApiOperation(value = "Get job description by job id", notes = "Get job description by job id")
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
  @ApiImplicitParams({
    @ApiImplicitParam(
        name = Constants.AUTH,
        value = Constants.TOKEN_TYPE,
        required = true,
        dataType = Constants.AUTH_DATA_TYPE,
        paramType = Constants.AUTH_PARAM_TYPE)
  })
  @GetMapping("/job/description/{jobId}")
  @ResponseStatus(HttpStatus.OK)
  public JobDto jobsDescriptionById(@PathVariable(value = "jobId") @NotNull String jobId) {
    return jobService.getJobDescription(jobId);
  }
}
