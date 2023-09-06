package com.prwatech.quiz.service;

import com.prwatech.common.razorpay.dto.CreateOrderDto;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import com.prwatech.quiz.dto.QuizAttemptDto;
import com.prwatech.quiz.dto.QuizContentGetDto;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public interface QuizUserService {

    Map<String, List<QuizContentGetDto>> getAllQuizListing(String userId, ObjectId quizId);

    QuizContentGetDto getQuizContentToAttempt(String userId, String quizId);

    QuizAttemptDto attemptAQuiz(QuizAttemptDto quizAttemptDto);

    RazorpayOrder getQuizToBuy(String userId, String quizId, CreateOrderDto createOrderDto);

    RazorpayOrder updateQuizOrder(String userId, String orderId, String paymentId);
}
