package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.SubmitEvaluationRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.EvaluationQuestion;
import com.prwatech.skillama.model.TesterEvaluationResponse;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.EvaluationQuestionRepository;
import com.prwatech.skillama.repository.TesterEvaluationResponseRepository;
import com.prwatech.skillama.util.IndiaTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TesterEvaluationResponseService {

    private final TesterEvaluationResponseRepository responseRepository;
    private final EvaluationQuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final CourseCurriculumRepository curriculumRepository;
    private final UserService userService;
    private final MongoTemplate skillamaMongoTemplate;

    public TesterEvaluationResponseService(
            TesterEvaluationResponseRepository responseRepository,
            EvaluationQuestionRepository questionRepository,
            CourseRepository courseRepository,
            CourseCurriculumRepository curriculumRepository,
            UserService userService,
            @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate) {
        this.responseRepository = responseRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.curriculumRepository = curriculumRepository;
        this.userService = userService;
        this.skillamaMongoTemplate = skillamaMongoTemplate;
    }

    public TesterEvaluationResponse submit(String testerId, SubmitEvaluationRequestDTO request) {
        if (!StringUtils.hasText(request.getCourseId())
                || !StringUtils.hasText(request.getCurriculumModuleId())
                || !StringUtils.hasText(request.getSubmoduleId())) {
            throw new IllegalArgumentException("courseId, curriculumModuleId and submoduleId are required");
        }

        List<EvaluationQuestion> activeQuestions = questionRepository.findByActiveTrueOrderByCategoryAscOrderAsc();
        if (activeQuestions.isEmpty()) {
            throw new IllegalArgumentException("No active evaluation questions configured");
        }

        Map<String, SubmitEvaluationRequestDTO.AnswerDTO> answersByQuestionId = (request.getAnswers() == null
                ? List.<SubmitEvaluationRequestDTO.AnswerDTO>of()
                : request.getAnswers()).stream()
                .filter(a -> a != null && StringUtils.hasText(a.getQuestionId()))
                .collect(Collectors.toMap(SubmitEvaluationRequestDTO.AnswerDTO::getQuestionId, a -> a, (a, b) -> a));

        List<TesterEvaluationResponse.EvaluationAnswer> answers = new java.util.ArrayList<>();
        boolean anyFlagged = false;
        for (EvaluationQuestion question : activeQuestions) {
            SubmitEvaluationRequestDTO.AnswerDTO given = answersByQuestionId.get(question.getId());
            if (given == null || given.getAnswer() == null) {
                throw new IllegalArgumentException("Missing answer for question: " + question.getQuestionText());
            }
            boolean isNo = given.getAnswer() == TesterEvaluationResponse.Answer.NO;
            if (isNo && !StringUtils.hasText(given.getFollowUpComment())) {
                throw new IllegalArgumentException(
                        "A follow-up comment is required for 'No' on: " + question.getQuestionText());
            }
            anyFlagged = anyFlagged || isNo;
            answers.add(TesterEvaluationResponse.EvaluationAnswer.builder()
                    .questionId(question.getId())
                    .questionTextSnapshot(question.getQuestionText())
                    .category(question.getCategory())
                    .answer(given.getAnswer())
                    .followUpComment(isNo ? given.getFollowUpComment().trim() : null)
                    .build());
        }

        User tester = userService.findById(testerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Course course = courseRepository.findById(request.getCourseId()).orElse(null);
        CourseCurriculum module = curriculumRepository.findById(request.getCurriculumModuleId()).orElse(null);
        String submoduleLabel = findSubmoduleLabel(module, request.getSubmoduleId());

        TesterEvaluationResponse response = TesterEvaluationResponse.builder()
                .testerId(testerId)
                .testerName(tester.getName())
                .testerEmail(tester.getEmail())
                .courseId(request.getCourseId())
                .courseNameSnapshot(course != null ? course.getName() : null)
                .curriculumModuleId(request.getCurriculumModuleId())
                .moduleNameSnapshot(module != null ? module.getModuleName() : null)
                .submoduleId(request.getSubmoduleId())
                .submoduleLabelSnapshot(submoduleLabel)
                .answers(answers)
                .hasFlaggedIssues(anyFlagged)
                .submittedAt(IndiaTime.now())
                .build();
        return responseRepository.save(response);
    }

    public Page<TesterEvaluationResponse> search(
            String courseId,
            String curriculumModuleId,
            String testerId,
            Boolean flaggedOnly,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        List<Criteria> criteria = new java.util.ArrayList<>();
        if (StringUtils.hasText(courseId)) {
            criteria.add(Criteria.where("courseId").is(courseId));
        }
        if (StringUtils.hasText(curriculumModuleId)) {
            criteria.add(Criteria.where("curriculumModuleId").is(curriculumModuleId));
        }
        if (StringUtils.hasText(testerId)) {
            criteria.add(Criteria.where("testerId").is(testerId));
        }
        if (Boolean.TRUE.equals(flaggedOnly)) {
            criteria.add(Criteria.where("hasFlaggedIssues").is(true));
        }
        if (from != null || to != null) {
            Criteria dateCriteria = Criteria.where("submittedAt");
            if (from != null) {
                dateCriteria = dateCriteria.gte(from);
            }
            if (to != null) {
                dateCriteria = dateCriteria.lte(to);
            }
            criteria.add(dateCriteria);
        }
        Query query = criteria.isEmpty()
                ? new Query()
                : new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        long total = skillamaMongoTemplate.count(query, TesterEvaluationResponse.class);
        query.with(pageable);
        List<TesterEvaluationResponse> content = skillamaMongoTemplate.find(query, TesterEvaluationResponse.class);
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    public TesterEvaluationResponse getById(String id) {
        return responseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation response not found"));
    }

    private String findSubmoduleLabel(CourseCurriculum module, String submoduleId) {
        if (module == null || module.getSubmodules() == null) {
            return null;
        }
        return module.getSubmodules().stream()
                .filter(s -> submoduleId.equals(s.getId()))
                .map(CourseCurriculum.Submodule::getLabel)
                .findFirst()
                .orElse(null);
    }
}
