package com.prwatech.finance.controller;

import com.prwatech.common.Constants;
import com.prwatech.common.razorpay.dto.CreateOrderDto;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import com.prwatech.common.razorpay.service.RazorpayService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment")
@AllArgsConstructor
public class OrderAndPaymentController {

    private final RazorpayService razorpayService;

    @ApiOperation(value = "Create new order on razorpay", notes = "Create new order on razorpay.")
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
    @PostMapping(value = "/create/{userId}")
    public RazorpayOrder createNewOrderForCourse(
            @PathVariable(value = "userId") String userId,
            @RequestBody CreateOrderDto createOrderDto
            ){
        return razorpayService.createNewOrder(createOrderDto, userId);
    }

    @ApiOperation(value = "Get razorpay order details by order id", notes = "Get razorpay order details by order id.")
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
    @GetMapping(value = "/get/{orderId}/{userId}")
    public RazorpayOrder getOrderByOrderId(
            @PathVariable(value = "userId") String userId,
            @PathVariable(value = "orderId") String orderId
    ){
        return razorpayService.getOrderByOrderId(orderId, userId);
    }

    @ApiOperation(value = "Update order status by order id", notes = "Update order status by order id.")
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
    @PatchMapping(value = "/update/{orderId}/{userId}/{paymentId}")
    public RazorpayOrder updateOrderStatus(
            @PathVariable(value = "userId") String userId,
            @PathVariable(value = "orderId") String orderId,
            @PathVariable(value = "paymentId") String paymentId
    ){
        return razorpayService.updateOrderAfterPayment(orderId, userId, paymentId);
    }

    @PatchMapping(value = "/update/applePay/{userId}/{courseId}/{paymentId}")
    public RazorpayOrder updateOrderStatusForApplePay(
            @PathVariable(value = "userId") String userId,
            @PathVariable(value = "courseId") String courseId,
            @PathVariable(value = "paymentId") String paymentId
    ){
        return razorpayService.updateOrderAfterApplePayPayment(userId, courseId, paymentId);
    }
}
