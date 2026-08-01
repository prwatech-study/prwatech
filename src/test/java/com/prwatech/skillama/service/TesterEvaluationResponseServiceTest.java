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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TesterEvaluationResponseServiceTest {

    @Mock private TesterEvaluationResponseRepository responseRepository;
    @Mock private EvaluationQuestionRepository questionRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseCurriculumRepository curriculumRepository;
    @Mock private UserService userService;
    @Mock private MongoTemplate mongoTemplate;

    private TesterEvaluationResponseService service;

    @BeforeEach
    void setUp() {
        service = new TesterEvaluationResponseService(
                responseRepository, questionRepository, courseRepository, curriculumRepository,
                userService, mongoTemplate);
        when(responseRepository.save(any(TesterEvaluationResponse.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private EvaluationQuestion question(String id, EvaluationQuestion.Category category, String text) {
        return EvaluationQuestion.builder().id(id).category(category).questionText(text).active(true).build();
    }

    private SubmitEvaluationRequestDTO.AnswerDTO answer(String questionId, TesterEvaluationResponse.Answer answer, String comment) {
        SubmitEvaluationRequestDTO.AnswerDTO dto = new SubmitEvaluationRequestDTO.AnswerDTO();
        dto.setQuestionId(questionId);
        dto.setAnswer(answer);
        dto.setFollowUpComment(comment);
        return dto;
    }

    private SubmitEvaluationRequestDTO request(List<SubmitEvaluationRequestDTO.AnswerDTO> answers) {
        SubmitEvaluationRequestDTO dto = new SubmitEvaluationRequestDTO();
        dto.setCourseId("course-1");
        dto.setCurriculumModuleId("module-1");
        dto.setSubmoduleId("sub-1");
        dto.setAnswers(answers);
        return dto;
    }

    @Test
    void submitRejectsMissingIdentifiers() {
        SubmitEvaluationRequestDTO dto = new SubmitEvaluationRequestDTO();
        dto.setAnswers(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.submit("tester-1", dto));
    }

    @Test
    void submitRejectsWhenNoActiveQuestionsConfigured() {
        when(questionRepository.findByActiveTrueOrderByCategoryAscOrderAsc()).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.submit("tester-1", request(List.of(answer("q1", TesterEvaluationResponse.Answer.YES, null)))));
    }

    @Test
    void submitRejectsMissingAnswerForAnActiveQuestion() {
        when(questionRepository.findByActiveTrueOrderByCategoryAscOrderAsc())
                .thenReturn(List.of(question("q1", EvaluationQuestion.Category.CONTENT, "Clear?")));

        assertThrows(IllegalArgumentException.class,
                () -> service.submit("tester-1", request(List.of()))); // no answers at all
    }

    @Test
    void submitRejectsNoAnswerWithoutFollowUpComment() {
        when(questionRepository.findByActiveTrueOrderByCategoryAscOrderAsc())
                .thenReturn(List.of(question("q1", EvaluationQuestion.Category.CONTENT, "Clear?")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.submit(
                "tester-1", request(List.of(answer("q1", TesterEvaluationResponse.Answer.NO, "  ")))));
        assertTrue(ex.getMessage().contains("follow-up comment"));
    }

    @Test
    void submitSucceedsAndComputesFlaggedIssuesAndSnapshots() {
        when(questionRepository.findByActiveTrueOrderByCategoryAscOrderAsc()).thenReturn(List.of(
                question("q1", EvaluationQuestion.Category.CONTENT, "Clear?"),
                question("q2", EvaluationQuestion.Category.IMAGE, "Relevant image?")));

        User tester = User.builder().id("tester-1").name("Jane Tester").email("jane@x.com").build();
        when(userService.findById("tester-1")).thenReturn(Optional.of(tester));

        Course course = new Course();
        course.setId("course-1");
        course.setName("Python Basics");
        when(courseRepository.findById("course-1")).thenReturn(Optional.of(course));

        CourseCurriculum.Submodule sub = new CourseCurriculum.Submodule();
        sub.setId("sub-1");
        sub.setLabel("Variables");
        CourseCurriculum module = new CourseCurriculum();
        module.setId("module-1");
        module.setModuleName("Module 1");
        module.setSubmodules(List.of(sub));
        when(curriculumRepository.findById("module-1")).thenReturn(Optional.of(module));

        TesterEvaluationResponse result = service.submit("tester-1", request(List.of(
                answer("q1", TesterEvaluationResponse.Answer.YES, "ignored for YES"),
                answer("q2", TesterEvaluationResponse.Answer.NO, "  Image doesn't match the topic  "))));

        assertTrue(result.isHasFlaggedIssues()); // one NO present
        assertEquals("Jane Tester", result.getTesterName());
        assertEquals("Python Basics", result.getCourseNameSnapshot());
        assertEquals("Module 1", result.getModuleNameSnapshot());
        assertEquals("Variables", result.getSubmoduleLabelSnapshot());
        assertEquals(2, result.getAnswers().size());

        TesterEvaluationResponse.EvaluationAnswer yesAnswer = result.getAnswers().stream()
                .filter(a -> a.getQuestionId().equals("q1")).findFirst().orElseThrow();
        assertNull(yesAnswer.getFollowUpComment()); // never persisted for YES, even if client sent one

        TesterEvaluationResponse.EvaluationAnswer noAnswer = result.getAnswers().stream()
                .filter(a -> a.getQuestionId().equals("q2")).findFirst().orElseThrow();
        assertEquals("Image doesn't match the topic", noAnswer.getFollowUpComment()); // trimmed
        assertEquals("Relevant image?", noAnswer.getQuestionTextSnapshot());
    }

    @Test
    void submitAllYesLeavesFlaggedIssuesFalse() {
        when(questionRepository.findByActiveTrueOrderByCategoryAscOrderAsc())
                .thenReturn(List.of(question("q1", EvaluationQuestion.Category.PRACTICAL, "Matches?")));
        when(userService.findById("tester-1")).thenReturn(Optional.of(User.builder().id("tester-1").build()));
        when(courseRepository.findById("course-1")).thenReturn(Optional.empty());
        when(curriculumRepository.findById("module-1")).thenReturn(Optional.empty());

        TesterEvaluationResponse result = service.submit("tester-1",
                request(List.of(answer("q1", TesterEvaluationResponse.Answer.YES, null))));

        assertFalse(result.isHasFlaggedIssues());
    }

    @Test
    void getByIdThrowsWhenNotFound() {
        when(responseRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getById("ghost"));
    }
}
