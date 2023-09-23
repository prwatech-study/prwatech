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
import com.prwatech.quiz.model.QuizContentAttemptMap;
import com.prwatech.quiz.model.QuizUserMapping;
import com.prwatech.quiz.repository.QuizAttemptMapTemplate;
import com.prwatech.quiz.repository.QuizAttemptRepository;
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
    private final QuizAttemptRepository quizAttemptRepository;
    private final OrdersRepository ordersRepository;
    private final RazorpayUtilityService razorpayUtilityService;
    private final OrderTemplate orderTemplate;

    private final QuizAttemptMapTemplate quizAttemptMapTemplate;

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

         QuizContentAttemptMap quizContentAttemptMap = QuizContentAttemptMap.builder()
                .attempt(0)
                .quizContentId(new ObjectId(quizContent.getId()))
                .userId(new ObjectId(userId))
                .quizId(quizContent.getQuizId())
                .currentScore(0)
                .lastScore(null)
                .build();
        quizContentAttemptMap = quizAttemptRepository.save(quizContentAttemptMap);

        QuizContentGetDto quizContentGetDto = new QuizContentGetDto();
        quizContentGetDto.setId(quizContent.getId());
        quizContentGetDto.setQuizId(quizContent.getQuizId().toString());
        quizContentGetDto.setQuizCategory(quizContent.getQuizCategory());
        quizContentGetDto.setTotalMarks(quizContent.getTotalMark());
        quizContentGetDto.setPassingMarks(quizContent.getPassingMark());
        quizContentGetDto.setQuizQuestionList(quizContent.getQuizQuestionList());
        quizContentGetDto.setTime(quizContent.getQuizQuestionList().size()*60);

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

        if(Objects.isNull(quizUserMapping) && quizContent.getQuizCategory().equals(QuizCategory.PAID)){
            throw new UnProcessableEntityException("User might attempt un-bought quiz.");
        }

        Integer totalMarks = quizContent.getTotalMark();
        Integer correctAns=0;
        Integer wrongAns=0;

        for(QuizQuestionDto quizQuestionDto : quizAttemptDto.getQuizQuestionDtoList()){
             if(quizQuestionDto.getAttemptedAnswer()!=null && quizQuestionDto.getAttemptedAnswer().equals(quizQuestionDto.getAnswer())){
                 correctAns++;
             }
             else {
                 wrongAns++;
             }
        }

        QuizContentAttemptMap quizContentAttemptMap = quizAttemptMapTemplate.findByUserIdQuizIdContentId(
               new ObjectId(quizAttemptDto.getUserId()),
                new ObjectId(quizContent.getId()));

        if(Objects.isNull(quizContentAttemptMap)){
            quizContentAttemptMap = QuizContentAttemptMap.builder()
                    .attempt(1)
                    .quizContentId(new ObjectId(quizContent.getId()))
                    .userId(new ObjectId(quizAttemptDto.getUserId()))
                    .quizId(quizContent.getQuizId())
                    .currentScore(correctAns)
                    .lastScore(null)
                    .build();
        }
        else{
            quizContentAttemptMap.setAttempt(quizContentAttemptMap.getAttempt()+1);
            quizContentAttemptMap.setLastScore((quizContentAttemptMap.getCurrentScore()==null)
                    ?null:quizContentAttemptMap.getCurrentScore());
            quizContentAttemptMap.setCurrentScore(correctAns);

        }
        quizContentAttemptMap = quizAttemptRepository.save(quizContentAttemptMap);
        quizAttemptDto.setAttempt(quizContentAttemptMap.getAttempt());

        if(Objects.isNull(quizUserMapping))
        {
            quizUserMapping = new QuizUserMapping();
            quizUserMapping.setQuizId(quizContent.getQuizId());
            quizUserMapping.setUserId(new ObjectId(quizAttemptDto.getUserId()));
            quizUserMapping.setIsOrdered((quizContent.getQuizCategory().equals(QuizCategory.UNPAID))
                    ?Boolean.TRUE:Boolean.FALSE);
            quizUserMapping.setAttempt(1);
            quizUserMapping.setCurrentScore(correctAns);
            quizUserMapping.setLastScore((quizUserMapping.getCurrentScore()==null)?null:quizUserMapping.getCurrentScore());
            //save mapping.
            quizUserMapping = quizUserMappingRepository.save(quizUserMapping);
        }

        quizAttemptDto.setCorrectAns(correctAns);
        quizAttemptDto.setWrongAns(wrongAns);
        Integer percentage =(correctAns*100/totalMarks);
        if(percentage<=33){
            quizAttemptDto.setResultCategory(ResultCategory.BAD);
        }
        else if(percentage>33 && percentage<=80){
            quizAttemptDto.setResultCategory(ResultCategory.GOOD);
        }
        else{
            quizAttemptDto.setResultCategory(ResultCategory.EXCELLENT);
        }

        if(correctAns==0){
            quizAttemptDto.setPercentage(0);
        }
        else{
            quizAttemptDto.setPercentage(percentage);
        }

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
