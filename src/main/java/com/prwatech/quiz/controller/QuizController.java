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

    @ApiOperation(value = "Add new quiz to database", notes = "Add new quiz to database.")
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

    @ApiOperation(value = "get quiz details by quiz id", notes = "get quiz details by quiz id")
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
    @GetMapping("/quiz/details/{id}")
    public QuizGetDto getQuizDetail(
            @PathVariable("id") String id
    ){
       return quizService.getQuizDetailsByQuizId(id);
    }

    @ApiOperation(value = "Add new quiz content to quiz ", notes = "Add new quiz content to quiz.")
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
    @PutMapping("/add/quiz/content/{id}")
    public List<QuizContent> addQuizContent(
            @PathVariable("id") String id,
            @RequestBody @NotNull List<QuizContentDto> quizContentDtoList
    ){
        return quizService.addNewQuizContent(new ObjectId(id), quizContentDtoList);
    }


    @ApiOperation(value = "Get all quiz Added.", notes = "Get all quiz Added.")
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

    @ApiOperation(value = "Get all quiz listing.", notes = "Get all quiz Listing.")
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
    @GetMapping("/get-all-quiz-listing")
    public List<QuizGetDto> getAllQuizList(){
        return quizService.getAllQuizList();
    }


    @ApiOperation(value = "Get all quiz content list.", notes = "Get all quiz content list.")
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
    @GetMapping("/quiz-content/listing-of-quiz/{id}")
    public Map<String, List<QuizContentGetDto>> getAllQuizContentListing(
            @PathVariable("id") String id,
            @RequestParam(value = "userId", required = false) String userId
    ){
        return quizUserService.getAllQuizListing(userId, new ObjectId(id));
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
    @DeleteMapping("/quiz/remove/{id}")
    public void removeAQuiz(@PathVariable("id") String id){
        quizService.deleteAQuizById(new ObjectId(id)); ;
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
    @DeleteMapping("/quiz-content/remove/{id}")
    public void removeAQuizContent(@PathVariable("id") String id){
        quizService.deleteAQuizContentByContentId(new ObjectId(id)); ;
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
            @PathVariable(value = "quizId") String id,
            @RequestBody @NotNull QuizDto quizDto
    ){
        return quizService.updateQuiz(id, quizDto);
    }
}
