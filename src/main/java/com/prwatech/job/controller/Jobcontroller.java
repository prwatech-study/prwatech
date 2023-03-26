package com.prwatech.job.controller;

import com.prwatech.job.dto.JobListDto;
import com.prwatech.job.service.JobService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job")
@AllArgsConstructor
public class Jobcontroller {

    private JobService jobService;
    @ApiOperation(
            value = "Get All Job Listing",
            notes = "Get All Job Listing"
    )
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
    @GetMapping("/")
    public JobListDto jobsList(){


return new JobListDto(jobService.getJobListing());

    }

}
