package com.prwatech.courses.controller;

import com.prwatech.common.dto.PaginationDto;
import com.prwatech.courses.dto.RegisterWebinarRequestDto;
import com.prwatech.courses.model.Webinar;
import com.prwatech.courses.service.WebinarService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;
import java.util.List;

@RestController
@RequestMapping("/api/public")
@AllArgsConstructor
public class WebinarAndEventController {

    private final WebinarService webinarService;

    @ApiOperation(value = "Get all Webinar listing", notes = " Get all Webinar listing.")
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
//    @ApiImplicitParams({
//            @ApiImplicitParam(
//                    name = Constants.AUTH,
//                    value = Constants.TOKEN_TYPE,
//                    required = true,
//                    dataType = Constants.AUTH_DATA_TYPE,
//                    paramType = Constants.AUTH_PARAM_TYPE)
//    })
    @GetMapping(value = "/webinar/all")
    public PaginationDto getAllWebinar(
            @RequestParam(value = "pageNumber", defaultValue = "0") Integer pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
            ){
           return webinarService.getAllWebinar(pageNumber, pageSize);
    }


    @ApiOperation(value = "Get Webinar by id", notes = " Get Webinar by id.")
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
//    @ApiImplicitParams({
//            @ApiImplicitParam(
//                    name = Constants.AUTH,
//                    value = Constants.TOKEN_TYPE,
//                    required = true,
//                    dataType = Constants.AUTH_DATA_TYPE,
//                    paramType = Constants.AUTH_PARAM_TYPE)
//    })
    @GetMapping(value = "/webinar/{id}")
    public Webinar getWebinarDetails(
            @PathVariable(value = "id") String id
    ){
        return webinarService.getWebinarById(id);
    }


    @ApiOperation(value = "Register to Webinar", notes = " Register to Webinar.")
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
//    @ApiImplicitParams({
//            @ApiImplicitParam(
//                    name = Constants.AUTH,
//                    value = Constants.TOKEN_TYPE,
//                    required = true,
//                    dataType = Constants.AUTH_DATA_TYPE,
//                    paramType = Constants.AUTH_PARAM_TYPE)
//    })
    @PutMapping(value = "/webinar/register")
    public void registerToWebinar(
            @RequestBody @NotNull RegisterWebinarRequestDto webinarRequestDto
            ){
           webinarService.registerWebinar(webinarRequestDto);
    }



    @ApiOperation(value = "Get all registered webinar", notes = "Get all registered webinar")
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
//    @ApiImplicitParams({
//            @ApiImplicitParam(
//                    name = Constants.AUTH,
//                    value = Constants.TOKEN_TYPE,
//                    required = true,
//                    dataType = Constants.AUTH_DATA_TYPE,
//                    paramType = Constants.AUTH_PARAM_TYPE)
//    })
    @GetMapping(value = "/webinar-registered/{email}")
    public List<Webinar> getRegisteredWebinar(
            @PathVariable(value = "email") String email
    ){
        return webinarService.getAllRegisteredWebinar(email);
    }
}
