package com.prwatech.notification.controller;

import com.prwatech.notification.dto.PushNotificationRequestDto;
import com.prwatech.notification.service.PushNotificationService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@AllArgsConstructor
public class PushNotificationController {
    private final PushNotificationService pushNotificationService;

    @ApiOperation(value = "Create new or update fcm token", notes = "Create new or update fcm token")
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
    @PutMapping("/create-update/fcm-token/{userId}")
    public Boolean createNewOrUpdateToken(
            @PathVariable(value = "userId") String userId,
            @RequestParam(value = "fcmToken") String fcmToken

    ){
       return pushNotificationService.createOrUpdateFcmToken(userId, fcmToken);
    }

    @ApiOperation(value = "Send new notification to users ", notes = "Send new notification to users")
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
    @PutMapping("/send-notifications")
    public String sendPushNotification(
            @RequestBody PushNotificationRequestDto requestDto
            ){
       return pushNotificationService.sendPushNotification(requestDto);
    }

}
