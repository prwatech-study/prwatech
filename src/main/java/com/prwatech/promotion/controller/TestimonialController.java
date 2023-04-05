package com.prwatech.promotion.controller;

import com.prwatech.promotion.model.Testimonials;
import com.prwatech.promotion.service.TestimonialService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/public")
public class TestimonialController {

  private final TestimonialService testimonialService;

  @ApiOperation(value = "Get all testimonials", notes = "Get all testimonials")
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
  @GetMapping("/testimonials/listing/")
  public List<Testimonials> getTestimonials() {
    return testimonialService.getAllTestimonials();
  }
}
