package com.prwatech.quiz.controller;

import com.prwatech.quiz.dto.QuizContentDto;
import com.prwatech.quiz.dto.QuizContentGetDto;
import com.prwatech.quiz.dto.QuizDto;
import com.prwatech.quiz.dto.QuizGetDto;
import com.prwatech.quiz.model.Quiz;
import com.prwatech.quiz.model.QuizContent;
import com.prwatech.quiz.service.QuizService;
import com.prwatech.quiz.service.QuizUserService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/admin")
@AllArgsConstructor
public class QuizController {

     private final QuizService quizService;
     private final QuizUserService quizUserService;

    @ApiOperation(value = "Add new quiz to database", notes = "Create / Add NEW QUIZ")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(value = HttpStatus.OK)
    @PostMapping("/add/quiz")
    public List<Quiz> addNewQuiz(
            @RequestBody @NotNull List<QuizDto> quizDtoList
            ){
        return quizService.addNewQuiz(quizDtoList);
    }

    @ApiOperation(value = "get quiz details by quiz id", notes = "GET QUIZ details by QUIZ ID")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(value = HttpStatus.OK)
    @GetMapping("/quiz/details/{quizId}")
    public QuizGetDto getQuizDetail(
            @PathVariable("quizId") String quizId
    ){
       return quizService.getQuizDetailsByQuizId(quizId);
    }

    @ApiOperation(value = "Add new quiz content to quiz ", notes = "Add new quiz questionairre set / content to quiz.")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(value = HttpStatus.OK)
    @PostMapping("/add/quiz/content/{quizId}")
    public List<QuizContent> addQuizContent(
            @PathVariable("quizId") String quizId,
            @RequestBody @NotNull List<QuizContentDto> quizContentDtoList
    ){
        return quizService.addNewQuizContent(new ObjectId(quizId), quizContentDtoList);
    }


    @ApiOperation(value = "Get all quiz Added.", notes = "Get all QUIZ with Content.")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(value = HttpStatus.OK)
    @GetMapping("/quiz/all")
    public List<Quiz> getAllAddedQuiz(){
        return quizService.getAllQuiz();
    }

    @ApiOperation(value = "Get all quiz listing.", notes = "Get all list of quiz")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(value = HttpStatus.OK)
    @GetMapping("/get-all-quiz")
    public List<QuizGetDto> getAllQuizList(){
        return quizService.getAllQuizList();
    }


    @ApiOperation(value = "Get all quiz content list.", notes = "Get all quiz content list by quiz Id")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(value = HttpStatus.OK)
    @GetMapping("/quiz-content/listing-of-quiz/{quizId}")
    public Map<String, List<QuizContentGetDto>> getAllQuizContentListing(
            @PathVariable("quizId") String quizId,
            @RequestParam(value = "userId", required = false) String userId
    ){
        return quizUserService.getAllQuizListing(userId, new ObjectId(quizId));
    }


    @ApiOperation(value = "Remove a quiz.", notes = "Remove a quiz.")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(value = HttpStatus.OK)
    @DeleteMapping("/quiz/remove/{quizId}")
    public void removeAQuiz(@PathVariable("quizId") String quizId){
        quizService.deleteAQuizById(new ObjectId(quizId)); ;
    }

    @ApiOperation(value = "Remove a quiz content.", notes = "Remove a quiz content.")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(value = HttpStatus.OK)
    @DeleteMapping("/quiz-content/remove/{quizContentId}")
    public void removeAQuizContent(@PathVariable("quizContentId") String quizContentId){
        quizService.deleteAQuizContentByContentId(new ObjectId(quizContentId)); ;
    }

    @ApiOperation(value = "update a quiz to database by id", notes = "update a quiz to database by id")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(value = HttpStatus.OK)
    @PutMapping("/update/quiz/{quizId}")
    public Quiz addNewQuiz(
            @PathVariable(value = "quizId") String quizId,
            @RequestBody @NotNull QuizDto quizDto
    ){
        return quizService.updateQuiz(quizId, quizDto);
    }

    @ApiOperation(value = "Update a single question in quiz content", notes = "Update a single question in quiz content by question index.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 400, message = "Not Available"),
            @ApiResponse(code = 401, message = "UnAuthorized"),
            @ApiResponse(code = 403, message = "Access Forbidden"),
            @ApiResponse(code = 404, message = "Not found"),
            @ApiResponse(code = 422, message = "UnProcessable entity"),
            @ApiResponse(code = 500, message = "Internal server error"),
    })
    @ResponseStatus(value = HttpStatus.OK)
    @PutMapping("/quiz-content/update-question/{quizContentId}/{questionIndex}")
    public QuizContent updateSingleQuestion(
            @PathVariable("quizContentId") String quizContentId,
            @PathVariable("questionIndex") int questionIndex,
            @RequestBody @NotNull com.prwatech.quiz.dto.QuizQuestionDto questionDto
    ) {
        return quizService.updateSingleQuestion(quizContentId, questionIndex, questionDto);
    }

    @ApiOperation(value = "Update all questions in quiz content", notes = "Update all questions in quiz content by quizContentId.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 400, message = "Not Available"),
            @ApiResponse(code = 401, message = "UnAuthorized"),
            @ApiResponse(code = 403, message = "Access Forbidden"),
            @ApiResponse(code = 404, message = "Not found"),
            @ApiResponse(code = 422, message = "UnProcessable entity"),
            @ApiResponse(code = 500, message = "Internal server error"),
    })
    @ResponseStatus(value = HttpStatus.OK)
    @PutMapping("/quiz-content/update-all-questions/{quizContentId}")
    public QuizContent updateAllQuestions(
            @PathVariable("quizContentId") String quizContentId,
            @RequestBody @NotNull java.util.List<com.prwatech.quiz.dto.QuizQuestionDto> questionDtoList
    ) {
        return quizService.updateAllQuestions(quizContentId, questionDtoList);
    }
}

