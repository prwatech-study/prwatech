package com.prwatech.quiz.controller;

import com.prwatech.common.Constants;
import com.prwatech.common.razorpay.dto.CreateOrderDto;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import com.prwatech.quiz.dto.QuizAttemptDto;
import com.prwatech.quiz.dto.QuizContentGetDto;
import com.prwatech.quiz.service.QuizUserService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
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

import javax.validation.constraints.NotNull;

@RequestMapping("/api/v1")
@AllArgsConstructor
public class UserQuizController {

    private final QuizUserService quizUserService;


    //GetAQuizContentToAttemptWithLastAttempt.
    @ApiOperation(value = "Get quiz-content to attempt", notes = "Get quiz-content to attempt")
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
    @GetMapping("/quiz-content/{id}")
    public QuizContentGetDto getQuizToAttempt(
            @RequestParam(value = "userId") String userId,
            @PathVariable(value = "id") String id){
        return quizUserService.getQuizContentToAttempt(userId, id);
    }

    //Attempt a quiz.
    @ApiOperation(value = "Attempt a quiz", notes = "Attempt a quiz")
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
    @PutMapping("/quiz-content/attempt/{id}")
    public QuizAttemptDto attemptAQuiz(
           @RequestBody @NotNull QuizAttemptDto quizAttemptDto)
    {
        return quizUserService.attemptAQuiz(quizAttemptDto);
    }

    @ApiOperation(value = "Buy a quiz ", notes = "Buy a quiz ")
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
    @PutMapping("/quiz/buy/{id}")
    public RazorpayOrder createOrderForQuiz(
            @RequestParam(value = "userId") String userId,
            @PathVariable(value = "id") String id,
            @RequestBody @NotNull CreateOrderDto createOrderDto
            ){
        return quizUserService.getQuizToBuy(userId, id, createOrderDto);
    }

    @ApiOperation(value = "update a quiz order ", notes = "update a quiz order")
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
    @PutMapping("/quiz-order/{orderId}")
    public RazorpayOrder updateOrderForQuiz(
            @RequestParam(value = "userId") String userId,
            @PathVariable(value = "orderId") String orderId,
            @RequestParam(value = "paymentId") String paymentId
    ){
        return quizUserService.updateQuizOrder(userId,orderId, paymentId);
    }

}
