package com.prwatech.coupon.controller;

import com.prwatech.common.Constants;
import com.prwatech.coupon.dto.AddCouponDto;
import com.prwatech.coupon.dto.GetCouponDto;
import com.prwatech.coupon.model.Coupon;
import com.prwatech.coupon.service.CouponService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
public class CouponController {

  private final CouponService couponService;

  @ApiOperation(
          value = "Add new coupon to database.",
          notes = "Add new coupon to database.")
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
  @PostMapping("/coupon/add")
  public List<Coupon> addNewCoupons(
          @RequestBody List<AddCouponDto> addCouponDtoList){
    return null;//couponService.addNewCoupons(addCouponDtoList);
  };

  @ApiOperation(
          value = "Get all coupon of user.",
          notes = "Get all  coupon of user.")
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
  @GetMapping("/coupon/{userId}")
  public List<GetCouponDto> getUsersCoupon(
         @PathVariable(value = "userId") String userId){
    return couponService.getAllCouponByUserId(new ObjectId(userId));}

  @ApiOperation(
          value = "Scratch a coupon of user.",
          notes = "Scratch a coupon of user.")
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
  @PatchMapping("/coupon/scratch/{userId}")
  public GetCouponDto scratchACoupon(
          @PathVariable(value = "userId") String userId,
          @RequestParam(value = "couponId") String couponId
  ){
    return couponService.scratchCoupon( new ObjectId(userId), new ObjectId(couponId));
  }

  @ApiOperation(
          value = "Redeem coupon of user.",
          notes = "Redeem coupon of user.")
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
  @PatchMapping("/coupon/redeem/{userId}")
  public GetCouponDto redeemCoupon(
          @PathVariable(value = "userId") String userId,
          @RequestParam(value = "code") String code
  ){
     return couponService.redeemCoupon(new ObjectId(userId), code);
  }

  @ApiOperation(
          value = "Add new coupon to User.",
          notes = "Add new coupon to User.")
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
  @PostMapping("/coupon/add/{userId}")
  public void allocateCouponToUser(
          @PathVariable(value = "userId") String userId,
          @RequestParam(value = "couponsId") List<String> couponIds
  ){
    couponService.allocateCouponsToUser(couponIds,new ObjectId(userId));
  }

  @ApiOperation(
          value = "Get all coupon in database.",
          notes = "Get all coupon in database.")
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
  @GetMapping("/coupon/all")
  public List<Coupon> getAllCoupons(){
    return couponService.getAllCoupon();
  }
//
//  @ApiOperation(
//          value = "Allocate temp coupon.",
//          notes = "Allocate tem coupon.")
//  @ApiResponses(
//          value = {
//                  @ApiResponse(code = 200, message = "Success"),
//                  @ApiResponse(code = 400, message = "Not Available"),
//                  @ApiResponse(code = 401, message = "UnAuthorized"),
//                  @ApiResponse(code = 403, message = "Access Forbidden"),
//                  @ApiResponse(code = 404, message = "Not found"),
//                  @ApiResponse(code = 422, message = "UnProcessable entity"),
//                  @ApiResponse(code = 500, message = "Internal server error"),
//          })
//  @ResponseStatus(value = HttpStatus.OK)
////  @ApiImplicitParams({
////          @ApiImplicitParam(
////                  name = Constants.AUTH,
////                  value = Constants.TOKEN_TYPE,
////                  required = true,
////                  dataType = Constants.AUTH_DATA_TYPE,
////                  paramType = Constants.AUTH_PARAM_TYPE)
////  })
//  @PostMapping("/coupon/temp-add")
//  public void assignmentToUsers(){
//    couponService.tempAllocateToAllUser();
//  }


}
