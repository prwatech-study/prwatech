package com.prwatech.quiz.service.impl;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.razorpay.RazorpayUtilityService;
import com.prwatech.common.razorpay.dto.CreateOrderDto;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import com.prwatech.finance.dto.RazorpayPayment;
import com.prwatech.finance.model.Orders;
import com.prwatech.finance.repository.OrdersRepository;
import com.prwatech.finance.repository.template.OrderTemplate;
import com.prwatech.quiz.dto.QuizAttemptDto;
import com.prwatech.quiz.dto.QuizContentGetDto;
import com.prwatech.quiz.dto.QuizQuestionDto;
import com.prwatech.quiz.enumuration.QuizCategory;
import com.prwatech.quiz.enumuration.ResultCategory;
import com.prwatech.quiz.model.QuizContent;
import com.prwatech.quiz.model.QuizUserMapping;
import com.prwatech.quiz.repository.QuizContentRepository;
import com.prwatech.quiz.repository.QuizContentTemplate;
import com.prwatech.quiz.repository.QuizRepository;
import com.prwatech.quiz.repository.QuizUserMappingRepository;
import com.prwatech.quiz.repository.QuizUserTemplate;
import com.prwatech.quiz.service.QuizUserService;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class QuizUserServiceImpl implements QuizUserService {

    private final QuizUserMappingRepository quizUserMappingRepository;
    private final QuizUserTemplate quizUserTemplate;
    private final QuizContentTemplate quizContentTemplate;
    private final QuizContentRepository quizContentRepository;
    private final QuizRepository quizRepository;
    private final OrdersRepository ordersRepository;
    private final RazorpayUtilityService razorpayUtilityService;
    private final OrderTemplate orderTemplate;

    @Override
    public Map<String, List<QuizContentGetDto>> getAllQuizListing(String userId, ObjectId quizId) {

        QuizUserMapping quizUserMapping=null;
        if(userId!=null){
            quizUserMapping = quizUserTemplate.getByUserIdAndQuizId(new ObjectId(userId), quizId);}
        List<QuizContent> quizContentList = quizContentTemplate.findByQuizId(quizId);

        List<QuizContentGetDto> quizContentGetDtoListFree = new ArrayList<>();
        List<QuizContentGetDto> quizContentGetDtoList = new ArrayList<>();
        for(QuizContent quizContent : quizContentList){
            QuizContentGetDto quizContentGetDto = new QuizContentGetDto();
            quizContentGetDto.setId(quizContent.getId());
            quizContentGetDto.setQuizId(quizId.toString());
            quizContentGetDto.setQuizCategory(quizContent.getQuizCategory());
            quizContentGetDto.setTotalMarks(quizContent.getTotalMark());
            quizContentGetDto.setPassingMarks(quizContent.getPassingMark());
            quizContentGetDto.setQuizQuestionList(null);
            quizContentGetDto.setIsPurchased((Objects.nonNull(quizUserMapping) && quizUserMapping.getIsOrdered().equals(Boolean.TRUE) )?Boolean.TRUE:Boolean.FALSE);
            if(quizContent.getQuizCategory().equals(QuizCategory.UNPAID)){
                quizContentGetDtoListFree.add(quizContentGetDto);
            }
            else {
                quizContentGetDtoList.add(quizContentGetDto);
            }

        }
        Map<String, List<QuizContentGetDto>> quizContentListMap=new HashMap<>();
        quizContentListMap.put("Free", quizContentGetDtoListFree);
        quizContentListMap.put("Paid", quizContentGetDtoList);

        return quizContentListMap;
    }

    @Override
    public QuizContentGetDto getQuizContentToAttempt(String userId, String quizContentId) {

        QuizContent quizContent = quizContentRepository.findById(quizContentId).orElseThrow(
                ()->new NotFoundException("No quiz listing found by this id: " + quizContentId));

        QuizUserMapping quizUserMapping = quizUserTemplate.getByUserIdAndQuizId(
                new ObjectId(userId), quizContent.getQuizId()
        );

        if(quizContent.getQuizCategory().equals(QuizCategory.PAID) && Objects.isNull(quizUserMapping)){
            throw new UnProcessableEntityException("User has no bought this quiz.");
        }

        QuizContentGetDto quizContentGetDto = new QuizContentGetDto();
        quizContentGetDto.setId(quizContent.getId());
        quizContentGetDto.setQuizId(quizContent.getQuizId().toString());
        quizContentGetDto.setQuizCategory(quizContent.getQuizCategory());
        quizContentGetDto.setTotalMarks(quizContent.getTotalMark());
        quizContentGetDto.setPassingMarks(quizContent.getPassingMark());
        quizContentGetDto.setQuizQuestionList(quizContent.getQuizQuestionList());

        return quizContentGetDto;
    }

    @Override
    public QuizAttemptDto attemptAQuiz(QuizAttemptDto quizAttemptDto) {

        //get quiz content;
        QuizContent quizContent = quizContentRepository.findById(quizAttemptDto.getQuizContentId()).orElseThrow(
                ()->new NotFoundException("No quiz listing found by this id: " + quizAttemptDto.getQuizContentId()));

        QuizUserMapping quizUserMapping = quizUserTemplate.getByUserIdAndQuizId(
                new ObjectId(quizAttemptDto.getUserId()), quizContent.getQuizId()
        );

        if(Objects.isNull(quizUserMapping)){
            throw new UnProcessableEntityException("User might attempt un-bought quiz.");
        }

        Integer totalMarks = quizContent.getTotalMark();
        Integer correctAns=0;
        Integer wrongAns=0;

        for(QuizQuestionDto quizQuestionDto : quizAttemptDto.getQuizQuestionDtoList()){
             if(quizQuestionDto.getAttemptedAnswer().equals(quizQuestionDto.getAnswer())){
                 correctAns++;
             }
             else {
                 wrongAns++;
             }
        }
        Integer division = (Integer) totalMarks/3;
        if(correctAns<=totalMarks && correctAns>=division*2){
            quizAttemptDto.setResultCategory(ResultCategory.EXCELLENT);
        }
        else if(correctAns>=division && correctAns<=division*2){
            quizAttemptDto.setResultCategory(ResultCategory.VERY_GOOD);
        }
        else{
            quizAttemptDto.setResultCategory(ResultCategory.GOOD);
        }

        quizUserMapping.setAttempt(quizUserMapping.getAttempt()+1);
        quizUserMapping.setCurrentScore(quizAttemptDto.getCorrectAns());
        quizUserMapping.setLastScore((quizUserMapping.getCurrentScore()==null)?null:quizUserMapping.getCurrentScore());

        //save mapping.
        quizUserMapping = quizUserMappingRepository.save(quizUserMapping);

        quizAttemptDto.setCorrectAns(correctAns);
        quizAttemptDto.setWrongAns(wrongAns);
        quizAttemptDto.setAttempt(quizUserMapping.getAttempt());
        quizAttemptDto.setPercentage((Integer) (quizAttemptDto.getWrongAns()/quizAttemptDto.getCorrectAns())*100);
        return quizAttemptDto;
    }

    @Override
    public RazorpayOrder getQuizToBuy(String userId, String quizId, CreateOrderDto createOrderDto) {

        createOrderDto.setReceipt(userId);
        createOrderDto.setPartialPayment(Boolean.FALSE);
        createOrderDto.setCurrency("INR");
        createOrderDto.setNotes(null);
        RazorpayOrder razorpayOrder = razorpayUtilityService.createOrder(createOrderDto);
        if(Objects.isNull(razorpayOrder)){
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


        QuizUserMapping quizUserMapping = new QuizUserMapping();
        quizUserMapping.setQuizId(new ObjectId(quizId));
        quizUserMapping.setUserId(new ObjectId(userId));
        quizUserMapping.setOrderId(orders.getOrderId());
        quizUserMapping.setIsOrdered(Boolean.FALSE);
        quizUserMapping.setAttempt(0);

        quizUserMappingRepository.save(quizUserMapping);
        razorpayOrder.setAmount(razorpayOrder.getAmount()/100);
        return razorpayOrder;

    }

    @Override
    public RazorpayOrder updateQuizOrder(String userId, String orderId, String paymentId) {

        QuizUserMapping quizUserMapping  = quizUserTemplate.getByUserIdAndOrderId(new ObjectId(userId), orderId);
        if(Objects.isNull(quizUserMapping)){
            throw new NotFoundException("No razorpay order exist by this order id "+ orderId);
        }

        RazorpayOrder razorpayOrder = razorpayUtilityService.getOrderById(orderId);

        if(Objects.isNull(razorpayOrder)){
            throw new UnProcessableEntityException("Getting empty response from razorpay! for this orderId :: " + orderId);
        }

        RazorpayPayment razorpayPayment = razorpayUtilityService.getPaymentByPaymentId(paymentId);
        if(Objects.isNull(razorpayPayment)){
            throw new UnProcessableEntityException("Getting empty response from razorpay! for this paymentId :: " + paymentId);
        }

        Orders orders = orderTemplate.getOrderByOrderId(orderId);
        orders.setStatus(razorpayOrder.getStatus());
        orders.setPaymentId(razorpayPayment.getPaymentId());
        ordersRepository.save(orders);

        quizUserMapping.setIsOrdered(razorpayPayment.getCaptured());
        quizUserMappingRepository.save(quizUserMapping);

        return razorpayOrder;
    }
}
