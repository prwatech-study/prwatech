package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CreateModuleQuizSessionRequestDTO;
import com.prwatech.skillama.dto.CreateModuleQuizSessionResponseDTO;
import com.prwatech.skillama.dto.ModuleQuizAttemptResultDTO;
import com.prwatech.skillama.dto.ModuleQuizOptionDTO;
import com.prwatech.skillama.dto.ModuleQuizQuestionDTO;
import com.prwatech.skillama.dto.SubmitModuleQuizAttemptRequestDTO;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.ModuleQuizAttempt;
import com.prwatech.skillama.model.ModuleQuizSession;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.ModuleQuizAttemptRepository;
import com.prwatech.skillama.repository.ModuleQuizSessionRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModuleQuizServiceTest {

    @Mock private ModuleQuizSessionRepository sessionRepository;
    @Mock private ModuleQuizAttemptRepository attemptRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseCurriculumRepository curriculumRepository;

    private ModuleQuizService service;

    private static final String COURSE = "c1";
    private static final String MODULE = "Python Basics";
    private static final String USER = "u1";

    @BeforeEach
    void setUp() {
        service = new ModuleQuizService(sessionRepository, attemptRepository,
                userProfileRepository, courseRepository, curriculumRepository);
        when(sessionRepository.save(any(ModuleQuizSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any(ModuleQuizAttempt.class))).thenAnswer(inv -> {
            ModuleQuizAttempt a = inv.getArgument(0);
            a.setId("attempt1");
            return a;
        });
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- fixtures ----------

    private ModuleQuizQuestionDTO question(int id, String correctKey) {
        return ModuleQuizQuestionDTO.builder()
                .id(id)
                .question("Q" + id)
                .correctKey(correctKey)
                .options(List.of(
                        ModuleQuizOptionDTO.builder().key("A").text("opt a").build(),
                        ModuleQuizOptionDTO.builder().key("B").text("opt b").build()))
                .build();
    }

    private CreateModuleQuizSessionRequestDTO validCreateRequest() {
        return CreateModuleQuizSessionRequestDTO.builder()
                .courseId(COURSE).moduleName(MODULE)
                .questions(new ArrayList<>(List.of(question(1, "A"), question(2, "B"))))
                .build();
    }

    private UserProfile eligibleProfile() {
        UserProfile p = new UserProfile();
        p.setUserId(USER);
        p.setIsGuest(false);
        UserProfile.CompletedLecture cl = new UserProfile.CompletedLecture();
        cl.setLectureLabel("L1");
        cl.setCourseId(COURSE);
        p.setCompletedLectures(new ArrayList<>(List.of(cl)));
        return p;
    }

    private CourseCurriculum moduleWithOneCompletedLecture() {
        CourseCurriculum.Submodule sub = new CourseCurriculum.Submodule();
        sub.setLabel("L1");
        sub.setEnabled(true);
        CourseCurriculum cc = CourseCurriculum.builder()
                .courseId(COURSE).moduleName(MODULE)
                .submodules(new ArrayList<>(List.of(sub)))
                .build();
        return cc;
    }

    private ModuleQuizSession sessionFor(String sessionId, String owner) {
        return ModuleQuizSession.builder()
                .quizSessionId(sessionId).userId(owner)
                .courseId(COURSE).moduleName(MODULE)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .questions(new ArrayList<>(List.of(
                        ModuleQuizSession.QuizQuestion.builder().id(1).question("Q1").correctKey("A")
                                .options(List.of(ModuleQuizSession.QuizOption.builder().key("A").text("a").build(),
                                        ModuleQuizSession.QuizOption.builder().key("B").text("b").build())).build(),
                        ModuleQuizSession.QuizQuestion.builder().id(2).question("Q2").correctKey("B")
                                .options(List.of(ModuleQuizSession.QuizOption.builder().key("A").text("a").build(),
                                        ModuleQuizSession.QuizOption.builder().key("B").text("b").build())).build())))
                .build();
    }

    // ---------- createSession validation ----------

    @Test
    void createSessionRejectsNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.createSession(null, USER, null));
    }

    @Test
    void createSessionRejectsMissingCourseId() {
        CreateModuleQuizSessionRequestDTO r = validCreateRequest();
        r.setCourseId(" ");
        assertThrows(IllegalArgumentException.class, () -> service.createSession(null, USER, r));
    }

    @Test
    void createSessionRejectsMissingModuleName() {
        CreateModuleQuizSessionRequestDTO r = validCreateRequest();
        r.setModuleName(null);
        assertThrows(IllegalArgumentException.class, () -> service.createSession(null, USER, r));
    }

    @Test
    void createSessionRejectsEmptyQuestions() {
        CreateModuleQuizSessionRequestDTO r = validCreateRequest();
        r.setQuestions(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> service.createSession(null, USER, r));
    }

    @Test
    void createSessionRejectsQuestionWithFewerThanTwoOptions() {
        CreateModuleQuizSessionRequestDTO r = validCreateRequest();
        r.getQuestions().get(0).setOptions(List.of(ModuleQuizOptionDTO.builder().key("A").text("only").build()));
        assertThrows(IllegalArgumentException.class, () -> service.createSession(null, USER, r));
    }

    @Test
    void createSessionRejectsQuestionMissingCorrectKey() {
        CreateModuleQuizSessionRequestDTO r = validCreateRequest();
        r.getQuestions().get(0).setCorrectKey(null);
        assertThrows(IllegalArgumentException.class, () -> service.createSession(null, USER, r));
    }

    // ---------- createSession eligibility ----------

    @Test
    void createSessionRejectsWhenProfileMissing() {
        when(userProfileRepository.findByUserId(USER)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.createSession(null, USER, validCreateRequest()));
    }

    @Test
    void createSessionRejectsWhenModuleAlreadyPassed() {
        UserProfile p = eligibleProfile();
        p.setPassedModuleQuizzes(new ArrayList<>(List.of(UserProfile.PassedModuleQuiz.builder()
                .courseId(COURSE).moduleName(MODULE).bestScore(2).maxScore(2).build())));
        when(userProfileRepository.findByUserId(USER)).thenReturn(Optional.of(p));
        assertThrows(IllegalArgumentException.class,
                () -> service.createSession(null, USER, validCreateRequest()));
    }

    @Test
    void createSessionRejectsWhenLecturesNotCompleted() {
        UserProfile p = eligibleProfile();
        p.setCompletedLectures(new ArrayList<>()); // nothing completed
        when(userProfileRepository.findByUserId(USER)).thenReturn(Optional.of(p));
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE))
                .thenReturn(List.of(moduleWithOneCompletedLecture()));
        assertThrows(IllegalArgumentException.class,
                () -> service.createSession(null, USER, validCreateRequest()));
    }

    @Test
    void createSessionSuccessHidesCorrectKeyAndReturnsQuestions() {
        when(userProfileRepository.findByUserId(USER)).thenReturn(Optional.of(eligibleProfile()));
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE))
                .thenReturn(List.of(moduleWithOneCompletedLecture()));

        CreateModuleQuizSessionResponseDTO res = service.createSession(null, USER, validCreateRequest());

        assertTrue(res.getSessionId().startsWith("quiz-"));
        assertEquals(2, res.getTotalQuestions());
        // Client questions must NOT leak the answer key.
        assertNull(res.getQuestions().get(0).getCorrectKey());
        verify(sessionRepository).save(any(ModuleQuizSession.class));
    }

    @Test
    void createSessionAllowedForGuestWithoutLectureCompletion() {
        UserProfile guest = new UserProfile();
        guest.setSessionId("guest-1");
        guest.setIsGuest(true);
        when(userProfileRepository.findBySessionId("guest-1")).thenReturn(Optional.of(guest));

        CreateModuleQuizSessionResponseDTO res = service.createSession("guest-1", null, validCreateRequest());
        assertEquals(2, res.getTotalQuestions());
    }

    // ---------- submitAttempt ----------

    @Test
    void submitAttemptRejectsMissingSessionId() {
        SubmitModuleQuizAttemptRequestDTO r = SubmitModuleQuizAttemptRequestDTO.builder()
                .answers(Map.of("1", "A")).build();
        assertThrows(IllegalArgumentException.class, () -> service.submitAttempt(null, USER, r));
    }

    @Test
    void submitAttemptRejectsMissingAnswers() {
        SubmitModuleQuizAttemptRequestDTO r = SubmitModuleQuizAttemptRequestDTO.builder()
                .sessionId("quiz-1").build();
        assertThrows(IllegalArgumentException.class, () -> service.submitAttempt(null, USER, r));
    }

    @Test
    void submitAttemptRejectsUnknownSession() {
        when(sessionRepository.findByQuizSessionId("quiz-x")).thenReturn(Optional.empty());
        SubmitModuleQuizAttemptRequestDTO r = SubmitModuleQuizAttemptRequestDTO.builder()
                .sessionId("quiz-x").answers(Map.of("1", "A")).build();
        assertThrows(IllegalArgumentException.class, () -> service.submitAttempt(null, USER, r));
    }

    @Test
    void submitAttemptRejectsExpiredSession() {
        ModuleQuizSession session = sessionFor("quiz-1", USER);
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(sessionRepository.findByQuizSessionId("quiz-1")).thenReturn(Optional.of(session));
        SubmitModuleQuizAttemptRequestDTO r = SubmitModuleQuizAttemptRequestDTO.builder()
                .sessionId("quiz-1").answers(Map.of("1", "A")).build();
        assertThrows(IllegalArgumentException.class, () -> service.submitAttempt(null, USER, r));
    }

    @Test
    void submitAttemptRejectsWrongOwner() {
        ModuleQuizSession session = sessionFor("quiz-1", "someoneElse");
        when(sessionRepository.findByQuizSessionId("quiz-1")).thenReturn(Optional.of(session));
        SubmitModuleQuizAttemptRequestDTO r = SubmitModuleQuizAttemptRequestDTO.builder()
                .sessionId("quiz-1").answers(Map.of("1", "A")).build();
        assertThrows(IllegalArgumentException.class, () -> service.submitAttempt(null, USER, r));
    }

    @Test
    void submitAttemptAllCorrectPasses() {
        ModuleQuizSession session = sessionFor("quiz-1", USER);
        when(sessionRepository.findByQuizSessionId("quiz-1")).thenReturn(Optional.of(session));
        when(attemptRepository.countByUserIdAndCourseIdAndModuleName(USER, COURSE, MODULE)).thenReturn(0);
        when(userProfileRepository.findByUserId(USER)).thenReturn(Optional.of(eligibleProfile()));

        SubmitModuleQuizAttemptRequestDTO r = SubmitModuleQuizAttemptRequestDTO.builder()
                .sessionId("quiz-1").answers(Map.of("1", "a", "2", "B")).build(); // case-insensitive

        ModuleQuizAttemptResultDTO result = service.submitAttempt(null, USER, r);

        assertEquals(2, result.getScore());
        assertEquals(2, result.getMaxScore());
        assertEquals(100.0, result.getPercentage());
        assertTrue(result.getPassed());
        assertEquals(1, result.getAttemptNumber());
        // Passing quiz records completion on profile.
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void submitAttemptBelowThresholdFails() {
        ModuleQuizSession session = sessionFor("quiz-1", USER);
        when(sessionRepository.findByQuizSessionId("quiz-1")).thenReturn(Optional.of(session));
        when(attemptRepository.countByUserIdAndCourseIdAndModuleName(USER, COURSE, MODULE)).thenReturn(1);

        SubmitModuleQuizAttemptRequestDTO r = SubmitModuleQuizAttemptRequestDTO.builder()
                .sessionId("quiz-1").answers(Map.of("1", "A", "2", "A")).build(); // 1/2 = 50%

        ModuleQuizAttemptResultDTO result = service.submitAttempt(null, USER, r);

        assertEquals(1, result.getScore());
        assertEquals(50.0, result.getPercentage());
        assertFalse(result.getPassed());
        assertEquals(2, result.getAttemptNumber());
        // No profile update when failing.
        verify(userProfileRepository, never()).save(any());
    }

    // ---------- skipModuleQuiz ----------

    @Test
    void skipRejectsMissingCourseOrModule() {
        assertThrows(IllegalArgumentException.class, () -> service.skipModuleQuiz(null, USER, "", MODULE));
        assertThrows(IllegalArgumentException.class, () -> service.skipModuleQuiz(null, USER, COURSE, " "));
    }

    @Test
    void skipRejectsWhenProfileMissing() {
        when(userProfileRepository.findByUserId(USER)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.skipModuleQuiz(null, USER, COURSE, MODULE));
    }

    @Test
    void skipRejectedForGuest() {
        UserProfile guest = new UserProfile();
        guest.setSessionId("guest-1");
        guest.setIsGuest(true);
        when(userProfileRepository.findBySessionId("guest-1")).thenReturn(Optional.of(guest));
        assertThrows(IllegalArgumentException.class, () -> service.skipModuleQuiz("guest-1", null, COURSE, MODULE));
    }

    @Test
    void skipReturnsAlreadyPassedWithoutSkipping() {
        UserProfile p = eligibleProfile();
        p.setPassedModuleQuizzes(new ArrayList<>(List.of(UserProfile.PassedModuleQuiz.builder()
                .courseId(COURSE).moduleName(MODULE).build())));
        when(userProfileRepository.findByUserId(USER)).thenReturn(Optional.of(p));

        Map<String, Object> res = service.skipModuleQuiz(null, USER, COURSE, MODULE);
        assertEquals(false, res.get("skipped"));
        assertEquals(true, res.get("alreadyPassed"));
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void skipRejectedBeforeMinAttempts() {
        when(userProfileRepository.findByUserId(USER)).thenReturn(Optional.of(eligibleProfile()));
        when(attemptRepository.countByUserIdAndCourseIdAndModuleName(USER, COURSE, MODULE)).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> service.skipModuleQuiz(null, USER, COURSE, MODULE));
    }

    @Test
    void skipSucceedsAfterMinAttempts() {
        UserProfile p = eligibleProfile();
        when(userProfileRepository.findByUserId(USER)).thenReturn(Optional.of(p));
        when(attemptRepository.countByUserIdAndCourseIdAndModuleName(USER, COURSE, MODULE))
                .thenReturn(ModuleQuizService.MIN_ATTEMPTS_BEFORE_SKIP);

        Map<String, Object> res = service.skipModuleQuiz(null, USER, COURSE, MODULE);

        assertEquals(true, res.get("skipped"));
        assertEquals(true, res.get("quizPending"));
        assertEquals(1, p.getSkippedModuleQuizzes().size());
        verify(userProfileRepository).save(p);
    }

    // ---------- pure predicates ----------

    @Test
    void hasPassedAndSkippedAndUnlockLogic() {
        UserProfile p = new UserProfile();
        p.setPassedModuleQuizzes(new ArrayList<>(List.of(UserProfile.PassedModuleQuiz.builder()
                .courseId(COURSE).moduleName(MODULE).bestScore(9).maxScore(10).build())));
        p.setSkippedModuleQuizzes(new ArrayList<>(List.of(UserProfile.SkippedModuleQuiz.builder()
                .courseId(COURSE).moduleName("Other").build())));

        assertTrue(service.hasPassedModuleQuiz(p, COURSE, MODULE));
        assertFalse(service.hasPassedModuleQuiz(p, COURSE, "Nope"));
        assertTrue(service.hasSkippedModuleQuiz(p, COURSE, "Other"));
        assertTrue(service.canUnlockPastModuleQuiz(p, COURSE, MODULE));   // passed
        assertTrue(service.canUnlockPastModuleQuiz(p, COURSE, "Other"));  // skipped
        assertFalse(service.canUnlockPastModuleQuiz(p, COURSE, "Unseen"));
        assertEquals(9, service.getBestQuizScore(p, COURSE, MODULE));
    }
}
