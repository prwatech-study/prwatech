package com.prwatech.quiz.service;

import com.prwatech.quiz.dto.QuizContentDto;
import com.prwatech.quiz.dto.QuizContentGetDto;
import com.prwatech.quiz.dto.QuizDto;
import com.prwatech.quiz.dto.QuizGetDto;
import com.prwatech.quiz.model.Quiz;
import com.prwatech.quiz.model.QuizContent;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface QuizService {

    List<Quiz> addNewQuiz(List<QuizDto> quizList);

    List<Quiz> getAllQuiz();

    List<QuizContent> addNewQuizContent(ObjectId quizId, List<QuizContentDto> quizContentList);

    List<QuizGetDto> getAllQuizList();

    QuizGetDto getQuizDetailsByQuizId(String id);

    List<QuizContentGetDto> getQuizContentByQuizId(ObjectId quizId);

    void deleteAQuizById(ObjectId id);

    void deleteAQuizContentByContentId(ObjectId id);
}
