package com.prwatech.user.controller;

import com.prwatech.common.Constants;
import com.prwatech.user.dto.UserDetailsDto;
import com.prwatech.user.dto.UserProfileDto;
import com.prwatech.user.dto.UserProfileUpdateDto;
import com.prwatech.user.service.UserService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
public class UserController {

  private final UserService userService;

  @ApiOperation(value = "Get user details by user id", notes = "Get user details by user id")
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
  @ResponseStatus(value = HttpStatus.OK)
  @ApiImplicitParams({
    @ApiImplicitParam(
        name = Constants.AUTH,
        value = Constants.TOKEN_TYPE,
        required = true,
        dataType = Constants.AUTH_DATA_TYPE,
        paramType = Constants.AUTH_PARAM_TYPE)
  })
  @GetMapping("/user/{id}")
  public UserDetailsDto getUserDetails(@PathVariable(value = "id") @NotNull String id) {
    return userService.getUserDetailsById(id);
  }

  @ApiOperation(value = "Get user profile by user id", notes = "Get user profile by user id")
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
  @ResponseStatus(value = HttpStatus.OK)
  @ApiImplicitParams({
    @ApiImplicitParam(
        name = Constants.AUTH,
        value = Constants.TOKEN_TYPE,
        required = true,
        dataType = Constants.AUTH_DATA_TYPE,
        paramType = Constants.AUTH_PARAM_TYPE)
  })
  @GetMapping("/user-profile/{id}")
  public UserProfileDto getUserProfile(@PathVariable(value = "id") @NotNull String id) {
    return userService.getUserProfileById(id);
  }

  @ApiOperation(
      value = "Update user details and education details",
      notes = "Update user details and education details")
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
  @ResponseStatus(value = HttpStatus.CREATED)
  @ApiImplicitParams({
    @ApiImplicitParam(
        name = Constants.AUTH,
        value = Constants.TOKEN_TYPE,
        required = true,
        dataType = Constants.AUTH_DATA_TYPE,
        paramType = Constants.AUTH_PARAM_TYPE)
  })
  @PutMapping("/user-profile/update/{id}")
  public void updateUserProfile(
      @PathVariable(value = "id") @NotNull String id,
      @RequestBody UserProfileUpdateDto profileUpdateDto) {
    userService.updateUserProfile(id, profileUpdateDto);
  }

  @ApiOperation(
      value = "Delete user details and education details",
      notes = "Delete user details and education details")
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
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  @ApiImplicitParams({
    @ApiImplicitParam(
        name = Constants.AUTH,
        value = Constants.TOKEN_TYPE,
        required = true,
        dataType = Constants.AUTH_DATA_TYPE,
        paramType = Constants.AUTH_PARAM_TYPE)
  })
  @DeleteMapping("/remove-education/{id}")
  public void deleteEducationDetails(@PathVariable(value = "id") @NotNull String id) {
    userService.deleteEducationDetailsById(id);
  }
}
