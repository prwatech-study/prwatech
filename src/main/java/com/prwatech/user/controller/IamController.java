package com.prwatech.user.controller;

import com.prwatech.common.Constants;
import com.prwatech.user.dto.SignInRequestDto;
import com.prwatech.user.dto.SignInResponseDto;
import com.prwatech.user.service.IamService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/iam")
@AllArgsConstructor
public class IamController {

    private final IamService iamService;


    @ApiOperation(
            value = "Sign in/up user via email and password or phone number",
            notes = "Sign in/up user via email and password or phone number")
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
    @PostMapping("/sign/in/up")
    public SignInResponseDto signInUser(
            @Valid @RequestBody() SignInRequestDto signInRequestDto,
            @RequestParam("isSignUp") Boolean isSignUp
            ){
        return iamService.signInWithPhoneOrEmailPassword(signInRequestDto, isSignUp);
    }

    @ApiOperation(
            value = "Sign in/up user via email and password or phone number",
            notes = "Sign in/up user via email and password or phone number")
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
    @PostMapping("/hello")
    public String helloWorld(){
        return "Hello world!";
    }
}
