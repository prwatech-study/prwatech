package com.prwatech.courses.controller;

import com.prwatech.common.Constants;

import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.service.CourseDetailService;
import com.prwatech.courses.service.FileServices;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.bson.types.ObjectId;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class UserCourseController {

    private final CourseDetailService courseDetailService;
    private final FileServices fileServices;

    @ApiOperation(value = "Get user enrolled course.", notes = " Get user enrolled courses.")
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
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @GetMapping(value = "/user-courses/{userId}")
    public Set<CourseCardDto> getAllEnrolledCourses(
            @PathVariable(value = "userId") String userId
    ){
        return courseDetailService.getAllUserEnrolledCourses(new ObjectId(userId));
    }

    @ApiOperation(value = "Get user completed course.", notes = " Get user completed courses.")
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
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @GetMapping(value = "/user-course/completed/{userId}")
    public Set<CourseCardDto> getAllCompletedCourses(
            @PathVariable(value = "userId") String userId
    ){
        return courseDetailService.getAllCompletedCourse(new ObjectId(userId));
    }



    @ApiOperation(value = "Update user course track.", notes = " Update user course track")
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
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @PutMapping(value = "/update/current-item/{courseId}")
    public void updateCurrentItem(
            @PathVariable(value = "courseId") String courseId,
            @RequestParam(value = "userId") String userId,
            @RequestParam(value = "currentItem") Integer currentItem
    ){
        courseDetailService.updateCurrentItem(
                new ObjectId(userId),
                new ObjectId(courseId),
                currentItem
        );
    }


    @ApiOperation(value = "Generate Certificate.", notes = " Generate Certificate")
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
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = Constants.AUTH,
                    value = Constants.TOKEN_TYPE,
                    required = true,
                    dataType = Constants.AUTH_DATA_TYPE,
                    paramType = Constants.AUTH_PARAM_TYPE)
    })
    @GetMapping(value = "/get/certificate")
    public ResponseEntity<ByteArrayResource> getCertificateByUserIdAndCourseId(
            @RequestParam(value = "userId") String userId,
            @RequestParam(value = "courseId") String courseId
    ) throws IOException {

        ByteArrayInputStream pdfStream = fileServices.generateCertificateByUserIdAndCourseId(userId,courseId);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "inline; filename=certificate.pdf");

        if(Objects.isNull(pdfStream)){
            throw new UnProcessableEntityException("Please try after sometime, Error in creating certificate.");
        }

        return ResponseEntity.ok().headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(IOUtils.toByteArray(pdfStream)));

    }


}
