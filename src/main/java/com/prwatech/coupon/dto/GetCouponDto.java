package com.prwatech.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetCouponDto {

    private String couponId;
    private String code;
    private String userId;
    private Integer amountOff;
    private Boolean isScratched;
    private Boolean isRedeemed;
    private Boolean isActive;
    private LocalDateTime validTillDate;
}
