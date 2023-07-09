package com.prwatech.common.razorpay.service;

import com.prwatech.common.razorpay.dto.CreateOrderDto;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import com.prwatech.finance.model.UserOrder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public interface RazorpayService {

    RazorpayOrder createNewOrder(CreateOrderDto createOrderDto, String userId);
    RazorpayOrder getOrderByOrderId(String orderId, String userId);
    RazorpayOrder updateOrderAfterPayment(String orderId, String userId, String paymentId);
}
