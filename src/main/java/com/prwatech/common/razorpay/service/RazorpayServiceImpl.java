package com.prwatech.common.razorpay.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.razorpay.RazorpayUtilityService;
import com.prwatech.common.razorpay.dto.CreateOrderDto;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import com.prwatech.finance.model.Orders;
import com.prwatech.finance.model.UserOrder;
import com.prwatech.finance.repository.OrdersRepository;
import com.prwatech.finance.repository.UserOrderRepository;
import com.prwatech.finance.repository.template.OrderTemplate;
import com.prwatech.finance.repository.template.UserOrderTemplate;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
@AllArgsConstructor
public class RazorpayServiceImpl implements RazorpayService{

    private final RazorpayUtilityService razorpayUtilityService;
    private final UserOrderTemplate userOrderTemplate;
    private final UserOrderRepository userOrderRepository;
    private final OrderTemplate orderTemplate;
    private final OrdersRepository ordersRepository;
    private final static Logger LOGGER = LoggerFactory.getLogger(RazorpayServiceImpl.class);

    @Override
    public RazorpayOrder createNewOrder(CreateOrderDto createOrderDto, String userId) {

        if(createOrderDto.getAmount()==null || createOrderDto.getAmount()<1){
            throw new UnProcessableEntityException("Invalid amount entered, please provide more one rupee.");
        }

        if(createOrderDto.getCurrency()==null){
            throw new UnProcessableEntityException("Currency can not be null!");
        }

        //Check for older order for same object.
        Optional<UserOrder> userOrderOptional = userOrderTemplate.getByUserIdAndCourseId(
                new ObjectId(createOrderDto.getCourseId()), new ObjectId(userId));

        if(userOrderOptional.isPresent() && userOrderOptional.get().getOrderId()!=null){
            LOGGER.info("One order is already created for course, checking for status!");

            //get order from razor pay if exist then return the order id,
            RazorpayOrder olderOrder = razorpayUtilityService.getOrderById(userOrderOptional.get().getOrderId());
            if(Objects.nonNull(olderOrder) && olderOrder.getStatus().equals("created")){
                return olderOrder;
            }

            //else delete these two entries and create new order.
            ordersRepository.deleteById(userOrderOptional.get().getOrderId());
            userOrderRepository.deleteById(userOrderOptional.get().getId());
        }

         RazorpayOrder razorpayOrder = razorpayUtilityService.createOrder(createOrderDto);
         if(Objects.isNull(razorpayOrder)){
             LOGGER.error("Something went wrong while creating order :: got null order response! ");
             throw new  UnProcessableEntityException("Unable to create order due to null response in order creation.");
         }

         //create orders and user order entry and save them.
         Orders orders = Orders.builder()
                 .orderId(razorpayOrder.getOrderId())
                 .amount(razorpayOrder.getAmount())
                 .entity(razorpayOrder.getEntity())
                 .amountPaid(razorpayOrder.getAmountPaid())
                 .amountDue(razorpayOrder.getAmountDue())
                 .currency(razorpayOrder.getCurrency())
                 .receipt(razorpayOrder.getReceipt())
                 .status(razorpayOrder.getStatus())
                 .attempts(razorpayOrder.getAttempts())
                 .notes(razorpayOrder.getNotes())
                 .creationTime(razorpayOrder.getCreatedAt())
                 .build();
         orders = ordersRepository.save(orders);

         UserOrder userOrder = UserOrder.builder().userId(new ObjectId(userId))
                 .courseId(new ObjectId(createOrderDto.getCourseId()))
                 .orders_id(new ObjectId(orders.getId()))
                 .orderId(orders.getOrderId())
                 .build();

         userOrderRepository.save(userOrder);
        return razorpayOrder;
    }

    @Override
    public RazorpayOrder getOrderByOrderId(String orderId, String userId) {

        //Check if the order exist or not.
        UserOrder userOrder = userOrderTemplate.getByOrderId(orderId, new ObjectId(userId));
        if(Objects.isNull(userOrder)){
            LOGGER.error("No razorpay order exist by given order id : {}", orderId);
            throw new NotFoundException("No razorpay order exist by this order id "+ orderId);
        }

        return razorpayUtilityService.getOrderById(orderId);
    }

    @Override
    public UserOrder updateOrderAfterPayment(String orderId, String userId) {

        UserOrder userOrder = userOrderTemplate.getByOrderId(orderId, new ObjectId(userId));
        if(Objects.isNull(userOrder)){
            LOGGER.error("No razorpay order exist by given order id : {}", orderId);
            throw new NotFoundException("No razorpay order exist by this order id "+ orderId);
        }

        RazorpayOrder razorpayOrder = getOrderByOrderId(orderId, userId);
        if(Objects.isNull(razorpayOrder)){
            LOGGER.error("Error :: Get order details from razorpay :: getting empty response!");
            throw new UnProcessableEntityException("Getting empty response from razorpay! for this orderId :: " + orderId);
        }

        //get order here and save both
        Orders orders = orderTemplate.getOrderByOrderId(orderId);
        orders.setStatus(razorpayOrder.getStatus());
        ordersRepository.save(orders);

        return userOrder;
    }
}
