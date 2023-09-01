package com.prwatech.quiz.service.impl;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.quiz.dto.QuizContentDto;
import com.prwatech.quiz.dto.QuizContentGetDto;
import com.prwatech.quiz.dto.QuizDto;
import com.prwatech.quiz.dto.QuizGetDto;

import com.prwatech.quiz.model.Quiz;
import com.prwatech.quiz.model.QuizContent;
import com.prwatech.quiz.repository.QuizContentRepository;
import com.prwatech.quiz.repository.QuizContentTemplate;
import com.prwatech.quiz.repository.QuizRepository;
import com.prwatech.quiz.service.QuizService;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


import static com.prwatech.common.Constants.DEFAULT_QUIZ_URL;

@Transactional
@AllArgsConstructor
@Component
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final QuizContentRepository quizContentRepository;
    private final QuizContentTemplate quizContentTemplate;

    @Override
    public List<Quiz> addNewQuiz(List<QuizDto> quizList) {

        List<Quiz> quizzes = new ArrayList<>();
        for(QuizDto quizDto : quizList){
            Quiz quiz = Quiz.builder()
                    .quizName(quizDto.getQuizName())
                    .secondaryTitle(quizDto.getSecondaryTitle())
                    .description(quizDto.getDescription())
                    .templateUrl((quizDto.getTemplateUrl()!=null)?quizDto.getTemplateUrl(): DEFAULT_QUIZ_URL)
                    .whyThisQuiz(quizDto.getWhyThisQuiz())
                    .price((quizDto.getPrice()!=null)? quizDto.getPrice() : 0)
                    .build();
            quizzes.add(quiz);

        }
        return quizRepository.saveAll(quizzes);
    }

    @Override
    public List<Quiz> getAllQuiz() {
        return quizRepository.findAll();
    }

    @Override
    public List<QuizContent> addNewQuizContent(ObjectId quizId, List<QuizContentDto> quizContentList) {

        Quiz quiz = quizRepository.findById(quizId.toString()).orElseThrow(
                ()-> new NotFoundException("No quiz found by this id: " + quizId));

        List<QuizContent> quizContents = new ArrayList<>();
        for(QuizContentDto quizContentDto : quizContentList){
            QuizContent quizContent = QuizContent.builder()
                    .quizId(quizId)
                    .quizCategory(quizContentDto.getQuizCategory())
                    .quizQuestionList(quizContentDto.getQuizQuestionList())
                    .totalMark(quizContentDto.getQuizQuestionList().size())
                    .passingMark(80)
                    .isDeleted(Boolean.FALSE)
                    .build();
            quizContents.add(quizContent);
        }
        quizContents = quizContentRepository.saveAll(quizContents);

        List<QuizContent> contents = quiz.getQuizContents();
        contents.addAll(quizContents);
        quiz.setQuizContents(contents);
        quizRepository.save(quiz);

        return quizContents;
    }

    @Override
    public List<QuizGetDto> getAllQuizList() {

        List<Quiz> quizzes = quizRepository.findAll();
        List<QuizGetDto> quizDtoList = new ArrayList<>();
        for(Quiz quiz: quizzes){
            QuizGetDto quizGetDto = new QuizGetDto();
            quizGetDto.setId(quiz.getId());
            quizGetDto.setQuizName(quiz.getQuizName());
            quizGetDto.setDescription(quizGetDto.getDescription());
            quizGetDto.setTemplateUrl(quiz.getTemplateUrl());
            quizGetDto.setQuizCount(quiz.getQuizContents().size());

            quizDtoList.add(quizGetDto);
        }
        return quizDtoList;
    }

    @Override
    public QuizGetDto getQuizDetailsByQuizId(String id) {

        Quiz quiz = quizRepository.findById(id).orElseThrow(
                ()-> new NotFoundException("No quiz found by this id : "+ id));

        QuizGetDto quizGetDto = new QuizGetDto();
        quizGetDto.setId(quiz.getId());
        quizGetDto.setQuizName(quiz.getQuizName());
        quizGetDto.setSecondaryTitle(quiz.getSecondaryTitle());
        quizGetDto.setDescription(quizGetDto.getDescription());
        quizGetDto.setTemplateUrl(quiz.getTemplateUrl());
        quizGetDto.setQuizCount(quiz.getQuizContents().size());
        quizGetDto.setPrice(quiz.getPrice());

        return quizGetDto;
    }

    @Override
    public void deleteAQuizById(ObjectId id) {
        quizRepository.deleteById(id.toHexString());
    }

    @Override
    public void deleteAQuizContentByContentId(ObjectId id) {
          QuizContent quizContent = quizContentRepository.findById(id.toString()).orElseThrow(
                  ()-> new NotFoundException("No quiz content found by this id!"));

          Quiz quiz = quizRepository.findById(quizContent.getQuizId().toString()).orElse(null);
          if(Objects.isNull(quiz) &&  !quiz.getQuizContents().isEmpty()){
              quizContentRepository.deleteById(id.toString());
              List<QuizContent> quizContents = quiz.getQuizContents();
              quizContents.remove(quizContent);
          }
          else{
              throw new UnProcessableEntityException("No quiz found by id.");
          }

          return;
    }

    @Override
    public Quiz updateQuiz(String quizId, QuizDto quizDto) {


            Quiz quiz = quizRepository.findById(quizId).orElseThrow(
                    ()-> new NotFoundException("No quiz found by this id : "+ quizId));
            if(quizDto.getQuizName()!=null){
                quiz.setQuizName(quizDto.getQuizName());
            }
            if(quizDto.getWhyThisQuiz()!=null){
               quiz.setWhyThisQuiz(quizDto.getWhyThisQuiz());
            }
            if(quizDto.getDescription()!=null){
                quiz.setDescription(quizDto.getDescription());
            }
            if(quizDto.getPrice()!=null){
               quiz.setPrice(quizDto.getPrice());
            }
            if(quizDto.getTemplateUrl()!=null){
               quiz.setTemplateUrl(quizDto.getTemplateUrl());
            }
            if(quizDto.getSecondaryTitle()!=null){
               quiz.setSecondaryTitle(quizDto.getSecondaryTitle());
            }

        return quizRepository.save(quiz);
    }
}
