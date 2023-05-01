package com.prwatech.promotion.controller;

import com.prwatech.promotion.model.PromotionBanner;
import com.prwatech.promotion.service.PromotionBannerService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/public/banner")
public class PromotionController {

   private final PromotionBannerService promotionBannerService;

    @ApiOperation(value = "Get all home page banner", notes = "Get all home page banner")
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
    @GetMapping("/listing")
    @ResponseStatus(HttpStatus.OK)
    public List<PromotionBanner> getHomePageBanner(){
        return promotionBannerService.getAllActiveBanner();
    }

}
