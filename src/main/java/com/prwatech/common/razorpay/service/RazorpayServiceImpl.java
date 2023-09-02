package com.prwatech.common.razorpay.service;

import com.prwatech.common.exception.AlreadyPresentException;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.razorpay.RazorpayUtilityService;
import com.prwatech.common.razorpay.dto.CreateOrderDto;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import com.prwatech.courses.model.Cart;
import com.prwatech.courses.model.CourseCurriculam;
import com.prwatech.courses.model.CourseTrack;
import com.prwatech.courses.repository.CartRepository;
import com.prwatech.courses.repository.CartTemplate;
import com.prwatech.courses.repository.CourseCurriculamTemplate;
import com.prwatech.courses.repository.CourseTrackRepository;
import com.prwatech.finance.dto.RazorpayPayment;
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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RazorpayServiceImpl implements RazorpayService{

    private final RazorpayUtilityService razorpayUtilityService;
    private final UserOrderTemplate userOrderTemplate;
    private final UserOrderRepository userOrderRepository;
    private final OrderTemplate orderTemplate;
    private final OrdersRepository ordersRepository;
    private final CartTemplate cartTemplate;
    private final CartRepository cartRepository;
    private final CourseTrackRepository courseTrackRepository;

    private final CourseCurriculamTemplate courseCurriculamTemplate;
    private final static Logger LOGGER = LoggerFactory.getLogger(RazorpayServiceImpl.class);

    @Override
    public RazorpayOrder createNewOrder(CreateOrderDto createOrderDto, String userId) {

        List<ObjectId> objectIdList=createOrderDto.getCourseIds().stream().map(ObjectId::new).collect(Collectors.toList());

        List<UserOrder> userOrderList = userOrderTemplate.getByUserIdAndCourseIds(new ObjectId(userId), objectIdList);

        if(!userOrderList.isEmpty()){
            throw new AlreadyPresentException("User have already bought some of these courses! please remove the bought courses." );
        }

        if(createOrderDto.getAmount()==null || createOrderDto.getAmount()<1){
            throw new UnProcessableEntityException("Invalid amount entered, please provide more one rupee.");
        }

        if(createOrderDto.getCurrency()==null){
            throw new UnProcessableEntityException("Currency can not be null!");
        }

         createOrderDto.setCurrency("INR");
         createOrderDto.setReceipt(userId);
         createOrderDto.setPartialPayment(Boolean.FALSE);
         RazorpayOrder razorpayOrder = razorpayUtilityService.createOrder(createOrderDto);
         if(Objects.isNull(razorpayOrder)){
             LOGGER.error("Something went wrong while creating order :: got null order response! ");
             throw new  UnProcessableEntityException("Unable to create order due to null response in order creation.");
         }

         //create orders and user order entry and save them.
         Orders orders = Orders.builder()
                 .orderId(razorpayOrder.getOrderId())
                 .amount(razorpayOrder.getAmount()/100)
                 .entity(razorpayOrder.getEntity())
                 .amountPaid(razorpayOrder.getAmountPaid()/100)
                 .amountDue(razorpayOrder.getAmountDue()/100)
                 .currency(razorpayOrder.getCurrency())
                 .receipt(razorpayOrder.getReceipt())
                 .status(razorpayOrder.getStatus())
                 .attempts(razorpayOrder.getAttempts())
                 .notes(razorpayOrder.getNotes())
                 .creationTime(razorpayOrder.getCreatedAt())
                 .build();
         orders = ordersRepository.save(orders);

         for (String courseId : createOrderDto.getCourseIds()){
         UserOrder userOrder = UserOrder.builder().userId(new ObjectId(userId))
                 .courseId(new ObjectId(courseId))
                 .orders_id(new ObjectId(orders.getId()))
                 .orderId(orders.getOrderId())
                 .build();
         userOrderRepository.save(userOrder);}
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

        RazorpayOrder razorpayOrder = razorpayUtilityService.getOrderById(orderId);
        if(Objects.nonNull(razorpayOrder)){
            razorpayOrder.setAmount(razorpayOrder.getAmount()/100);
            razorpayOrder.setAmountDue(razorpayOrder.getAmountDue()/100);
            razorpayOrder.setAmountPaid(razorpayOrder.getAmountPaid()/100);
        }

        return razorpayOrder;
    }

    @Override
    public RazorpayOrder updateOrderAfterPayment(String orderId, String userId, String paymentId) {

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

        LOGGER.info("Getting payment details against payment id : {} ", paymentId);
        RazorpayPayment razorpayPayment = razorpayUtilityService.getPaymentByPaymentId(paymentId);

        if(Objects.isNull(razorpayPayment)){
            LOGGER.error("Error :: Get payment details from razorpay :: getting empty response!");
            throw new UnProcessableEntityException("Getting empty response from razorpay! for this paymentId :: " + paymentId);
        }

        userOrder.setIsCompleted(razorpayPayment.getCaptured());
        orders.setPaymentId(razorpayPayment.getPaymentId());
        ordersRepository.save(orders);
        userOrderRepository.save(userOrder);

        Cart cart = cartTemplate.getByUserIdAndCourseId(new ObjectId(userId), userOrder.getCourseId());
        if(Objects.nonNull(cart)){
            cartRepository.deleteById(cart.getId());
        }

        CourseCurriculam courseCurriculam = courseCurriculamTemplate.getAllCurriculamByCourseId(userOrder.getCourseId());

        //if payment successful then courses will be assigned
        if(razorpayPayment.getCaptured()){
            LOGGER.info("The given course has been assigned to user.");
            CourseTrack courseTrack = CourseTrack.builder()
                    .userId(new ObjectId(userId))
                    .courseId(userOrder.getCourseId())
                    .currentItem(1)
                    .isAllCompleted(Boolean.FALSE)
                    .totalSize((Objects.nonNull(courseCurriculam))?courseCurriculam.getCourse_Curriculam().size():1)
                    .build();

            courseTrackRepository.save(courseTrack);
        }

        return razorpayOrder;
    }
}
