package com.prwatech.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConstantEndPoint {


    public static final String RAZORPAY_END_POINT="https://api.razorpay.com/v2";
    public static final String FETCH_CREATE_ORDER="/orders";
}
