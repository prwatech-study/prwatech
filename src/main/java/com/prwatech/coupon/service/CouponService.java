package com.prwatech.coupon.service;

import com.prwatech.coupon.dto.AddCouponDto;
import com.prwatech.coupon.dto.GetCouponDto;
import com.prwatech.coupon.model.Coupon;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface CouponService {

    List<Coupon> addNewCoupons(List<AddCouponDto> addCouponDtoList);

    List<GetCouponDto> getAllCouponByUserId(ObjectId userId);

    GetCouponDto scratchCoupon(ObjectId userId, ObjectId couponId);

    GetCouponDto redeemCoupon(ObjectId userId, String code);

    List<Coupon> getAllCoupon();

    void allocateCouponsToUser(List<String> couponIds, ObjectId userId);

    void tempAllocateToAllUser();


}
