package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AdminExamAttemptDTO;
import com.prwatech.skillama.dto.AdminExamRecommendationDTO;
import com.prwatech.skillama.dto.AreaLinkDTO;
import com.prwatech.skillama.dto.ExamAttemptResultDTO;
import com.prwatech.skillama.dto.ExamFeedbackResponseDTO;
import com.prwatech.skillama.dto.ExamRecommendationResponseDTO;
import com.prwatech.skillama.dto.ExamResultDashboardDTO;
import com.prwatech.skillama.dto.GeneratedQuizDTO;
import com.prwatech.skillama.dto.ModuleQuizAttemptSummaryDTO;
import com.prwatech.skillama.dto.ModuleQuizOptionDTO;
import com.prwatech.skillama.dto.ModuleQuizQuestionDTO;
import com.prwatech.skillama.dto.StartExamRequestDTO;
import com.prwatech.skillama.dto.StartExamResponseDTO;
import com.prwatech.skillama.dto.SubmitExamAttemptRequestDTO;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.ExamAttempt;
import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamRecommendationLog;
import com.prwatech.skillama.model.ExamSession;
import com.prwatech.skillama.model.ExamType;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.ExamAttemptRepository;
import com.prwatech.skillama.repository.ExamRecommendationLogRepository;
import com.prwatech.skillama.repository.ExamSessionRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamServiceTest {

    @Mock private ExamSessionRepository sessionRepository;
    @Mock private ExamAttemptRepository attemptRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private SkillamaAiClient skillamaAiClient;
    @Mock private AiUsageService aiUsageService;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private ModuleQuizService moduleQuizService;
    @Mock private ExamRecommendationLogRepository recommendationLogRepository;
    @Mock private CourseCurriculumRepository curriculumRepository;

    private ExamService service;

    private static final String USER = "u1";
    private static final String COURSE = "c1";

    @BeforeEach
    void setUp() {
        service = new ExamService(sessionRepository, attemptRepository, courseRepository,
                skillamaAiClient, aiUsageService, userRepository, moduleQuizService,
                recommendationLogRepository, curriculumRepository);

        when(userRepository.findById(USER)).thenReturn(Optional.of(User.builder().id(USER).build()));
        when(courseRepository.findById(COURSE)).thenReturn(Optional.of(Course.builder().id(COURSE).name("Python").build()));
        when(sessionRepository.save(any(ExamSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(inv -> {
            ExamAttempt a = inv.getArgument(0);
            a.setId("attempt1");
            return a;
        });
        when(skillamaAiClient.generateQuizQuestions(anyString(), anyString(), anyList(), anyInt(), any()))
                .thenReturn(generatedQuiz());
    }

    private ModuleQuizQuestionDTO question(int id, String correctKey) {
        return ModuleQuizQuestionDTO.builder()
                .id(id).question("Q" + id).correctKey(correctKey)
                .options(List.of(
                        ModuleQuizOptionDTO.builder().key("A").text("opt a").build(),
                        ModuleQuizOptionDTO.builder().key("B").text("opt b").build()))
                .build();
    }

    private GeneratedQuizDTO generatedQuiz() {
        return GeneratedQuizDTO.builder()
                .quizTitle("AI Exam: Python")
                .questions(new ArrayList<>(List.of(question(1, "A"), question(2, "B"))))
                .modelId("test-model").inputTokens(5).outputTokens(15).totalTokens(20)
                .build();
    }

    private StartExamRequestDTO practiceRequest() {
        StartExamRequestDTO r = new StartExamRequestDTO();
        r.setCourseId(COURSE);
        r.setDifficulty(ExamDifficulty.BEGINNER);
        r.setExamType(ExamType.PRACTICE);
        return r;
    }

    private ExamSession sessionFor(String sessionId, String owner, LocalDateTime startedAt) {
        return ExamSession.builder()
                .examSessionId(sessionId).userId(owner)
                .courseId(COURSE)
                .difficulty(ExamDifficulty.BEGINNER).examType(ExamType.PRACTICE)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .startedAt(startedAt)
                .timeLimitSeconds(300)
                .questions(new ArrayList<>(List.of(
                        ExamSession.ExamQuestion.builder().id(1).question("Q1").correctKey("A")
                                .options(List.of(ExamSession.ExamOption.builder().key("A").text("a").build(),
                                        ExamSession.ExamOption.builder().key("B").text("b").build())).build(),
                        ExamSession.ExamQuestion.builder().id(2).question("Q2").correctKey("B")
                                .options(List.of(ExamSession.ExamOption.builder().key("A").text("a").build(),
                                        ExamSession.ExamOption.builder().key("B").text("b").build())).build())))
                .build();
    }

    // ---------- startExam validation ----------

    @Test
    void startExamRejectsNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.startExam(USER, null));
    }

    @Test
    void startExamRejectsMissingDifficulty() {
        StartExamRequestDTO r = practiceRequest();
        r.setDifficulty(null);
        assertThrows(IllegalArgumentException.class, () -> service.startExam(USER, r));
    }

    @Test
    void startExamRejectsModuleWiseWithoutModuleId() {
        StartExamRequestDTO r = practiceRequest();
        r.setExamType(ExamType.MODULE_WISE);
        assertThrows(IllegalArgumentException.class, () -> service.startExam(USER, r));
    }

    @Test
    void startExamRejectsTopicWiseWithoutTopic() {
        StartExamRequestDTO r = practiceRequest();
        r.setExamType(ExamType.TOPIC_WISE);
        assertThrows(IllegalArgumentException.class, () -> service.startExam(USER, r));
    }

    @Test
    void startExamThrowsWhenAiReturnsNoQuestions() {
        when(skillamaAiClient.generateQuizQuestions(anyString(), anyString(), anyList(), anyInt(), any()))
                .thenReturn(GeneratedQuizDTO.builder().questions(new ArrayList<>()).build());
        assertThrows(IllegalStateException.class, () -> service.startExam(USER, practiceRequest()));
    }

    @Test
    void startExamRejectsWhenBudgetExhausted() {
        org.mockito.Mockito.doThrow(new AiBudgetLimitException("limit reached", 5.0, 5.0))
                .when(aiUsageService).assertWithinBudget(any(User.class));

        assertThrows(AiBudgetLimitException.class, () -> service.startExam(USER, practiceRequest()));
        verify(skillamaAiClient, never()).generateQuizQuestions(anyString(), anyString(), anyList(), anyInt(), any());
    }

    @Test
    void startExamSuccessHidesCorrectKeyAndScalesTimeLimitByDifficulty() {
        StartExamRequestDTO r = practiceRequest();
        r.setDifficulty(ExamDifficulty.ADVANCED);

        StartExamResponseDTO res = service.startExam(USER, r);

        assertTrue(res.getExamSessionId().startsWith("exam-"));
        assertEquals(2, res.getTotalQuestions());
        assertNull(res.getQuestions().get(0).getCorrectKey());
        assertEquals(10 * 90, res.getTimeLimitSeconds()); // ADVANCED -> 10 questions * 90s
        verify(sessionRepository).save(any(ExamSession.class));
        verify(aiUsageService).recordUsage(any());
    }

    // ---------- submitAttempt ----------

    @Test
    void submitAttemptRejectsMissingSessionId() {
        SubmitExamAttemptRequestDTO r = SubmitExamAttemptRequestDTO.builder().answers(Map.of("1", "A")).build();
        assertThrows(IllegalArgumentException.class, () -> service.submitAttempt(USER, r));
    }

    @Test
    void submitAttemptRejectsUnknownSession() {
        when(sessionRepository.findByExamSessionId("exam-x")).thenReturn(Optional.empty());
        SubmitExamAttemptRequestDTO r = SubmitExamAttemptRequestDTO.builder()
                .examSessionId("exam-x").answers(Map.of("1", "A")).build();
        assertThrows(IllegalArgumentException.class, () -> service.submitAttempt(USER, r));
    }

    @Test
    void submitAttemptRejectsExpiredSession() {
        ExamSession session = sessionFor("exam-1", USER, LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(sessionRepository.findByExamSessionId("exam-1")).thenReturn(Optional.of(session));
        SubmitExamAttemptRequestDTO r = SubmitExamAttemptRequestDTO.builder()
                .examSessionId("exam-1").answers(Map.of("1", "A")).build();
        assertThrows(IllegalArgumentException.class, () -> service.submitAttempt(USER, r));
    }

    @Test
    void submitAttemptRejectsWrongOwner() {
        ExamSession session = sessionFor("exam-1", "someoneElse", LocalDateTime.now());
        when(sessionRepository.findByExamSessionId("exam-1")).thenReturn(Optional.of(session));
        SubmitExamAttemptRequestDTO r = SubmitExamAttemptRequestDTO.builder()
                .examSessionId("exam-1").answers(Map.of("1", "A")).build();
        assertThrows(IllegalArgumentException.class, () -> service.submitAttempt(USER, r));
    }

    @Test
    void submitAttemptGradesCorrectlyAndComputesServerSideTime() {
        ExamSession session = sessionFor("exam-1", USER, LocalDateTime.now());
        when(sessionRepository.findByExamSessionId("exam-1")).thenReturn(Optional.of(session));

        SubmitExamAttemptRequestDTO r = SubmitExamAttemptRequestDTO.builder()
                .examSessionId("exam-1").answers(Map.of("1", "a", "2", "B")).build();

        ExamAttemptResultDTO result = service.submitAttempt(USER, r);

        assertEquals(2, result.getScore());
        assertEquals(2, result.getMaxScore());
        assertEquals(100.0, result.getPercentage());
        assertTrue(result.getTimeSpentSeconds() != null && result.getTimeSpentSeconds() >= 0);
        assertFalse(result.getOverTimeLimit());
    }

    @Test
    void submitAttemptFlagsOverTimeLimit() {
        ExamSession session = sessionFor("exam-1", USER, LocalDateTime.now().minusSeconds(400));
        when(sessionRepository.findByExamSessionId("exam-1")).thenReturn(Optional.of(session));

        SubmitExamAttemptRequestDTO r = SubmitExamAttemptRequestDTO.builder()
                .examSessionId("exam-1").answers(Map.of("1", "A", "2", "B")).build();

        ExamAttemptResultDTO result = service.submitAttempt(USER, r);

        assertTrue(result.getTimeSpentSeconds() >= 400);
        assertTrue(result.getOverTimeLimit());
    }

    // ---------- listMyAttempts ----------

    @Test
    void listMyAttemptsFiltersByCourseWhenProvided() {
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE))
                .thenReturn(List.of(ExamAttempt.builder().id("a1").userId(USER).courseId(COURSE).build()));

        assertEquals(1, service.listMyAttempts(USER, COURSE).size());
    }

    // ---------- getRecommendation ----------

    @Test
    void getRecommendationUsesAverageModuleQuizScoreAndRecordsUsage() {
        when(moduleQuizService.getAttempts(null, USER, COURSE, null)).thenReturn(List.of(
                ModuleQuizAttemptSummaryDTO.builder().percentage(80.0).build(),
                ModuleQuizAttemptSummaryDTO.builder().percentage(60.0).build()));
        when(skillamaAiClient.getExamRecommendation(anyString(), any(), any())).thenReturn(
                ExamRecommendationResponseDTO.builder()
                        .difficulty(ExamDifficulty.INTERMEDIATE)
                        .topic("Loops").reasoning("steady progress")
                        .estimatedMinutes(15).expectedScorePercent(80)
                        .modelId("m").inputTokens(1).outputTokens(2).totalTokens(3)
                        .build());

        ExamRecommendationResponseDTO res = service.getRecommendation(USER, COURSE);

        assertEquals(ExamDifficulty.INTERMEDIATE, res.getDifficulty());
        assertEquals("Loops", res.getTopic());
        verify(skillamaAiClient).getExamRecommendation("Python", null, 70.0);
        verify(aiUsageService).recordUsage(any());
        verify(recommendationLogRepository).save(any(ExamRecommendationLog.class));
    }

    // ---------- listAdminRecommendations ----------

    @Test
    void listAdminRecommendationsFiltersByCourseAndIncludesUserDetails() {
        ExamRecommendationLog log = ExamRecommendationLog.builder()
                .id("r1").userId(USER).courseId(COURSE)
                .difficulty(ExamDifficulty.ADVANCED).topic("Recursion")
                .reasoning("high scores").estimatedMinutes(20).expectedScorePercent(85)
                .createdAt(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build();
        when(recommendationLogRepository.findAll()).thenReturn(List.of(log));
        when(userRepository.findById(USER)).thenReturn(Optional.of(
                User.builder().id(USER).name("Ada").email("ada@skillama.co.in").build()));

        Page<AdminExamRecommendationDTO> page = service.listAdminRecommendations(0, 20, null, COURSE, null);

        assertEquals(1, page.getTotalElements());
        AdminExamRecommendationDTO row = page.getContent().get(0);
        assertEquals("Ada", row.getUserName());
        assertEquals("Python", row.getCourseName());
        assertEquals("Recursion", row.getTopic());
        assertEquals(ExamDifficulty.ADVANCED, row.getDifficulty());
    }

    @Test
    void listAdminRecommendationsFiltersByEmail() {
        ExamRecommendationLog log = ExamRecommendationLog.builder()
                .id("r1").userId(USER).courseId(COURSE).build();
        when(recommendationLogRepository.findAll()).thenReturn(List.of(log));
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().id(USER).email("ada@skillama.co.in").build(),
                User.builder().id("someone-else").email("bob@skillama.co.in").build()));

        Page<AdminExamRecommendationDTO> noMatch =
                service.listAdminRecommendations(0, 20, null, null, "bob@");
        assertEquals(0, noMatch.getTotalElements());

        Page<AdminExamRecommendationDTO> match =
                service.listAdminRecommendations(0, 20, null, null, "ada@");
        assertEquals(1, match.getTotalElements());
    }

    // ---------- listAdminAttempts ----------

    @Test
    void listAdminAttemptsFiltersByCourseAndIncludesUserDetails() {
        ExamAttempt a1 = ExamAttempt.builder().id("a1").userId(USER).courseId(COURSE)
                .score(4).maxScore(5).percentage(80.0)
                .submittedAt(LocalDateTime.of(2026, 6, 1, 10, 0)).build();
        when(attemptRepository.findAll()).thenReturn(List.of(a1));
        when(userRepository.findById(USER)).thenReturn(Optional.of(
                User.builder().id(USER).name("Ada").email("ada@skillama.co.in").build()));

        Page<AdminExamAttemptDTO> page = service.listAdminAttempts(0, 20, null, COURSE, null);

        assertEquals(1, page.getTotalElements());
        AdminExamAttemptDTO row = page.getContent().get(0);
        assertEquals("Ada", row.getUserName());
        assertEquals("Python", row.getCourseName());
        assertEquals(80.0, row.getPercentage());
    }

    // ---------- getResultDashboard ----------

    private ExamAttempt attemptOf(
            String id, double percentage, ExamType type, ExamDifficulty difficulty, String topic, String moduleId) {
        return ExamAttempt.builder()
                .id(id).userId(USER).courseId(COURSE)
                .examType(type).difficulty(difficulty)
                .topic(topic).moduleId(moduleId)
                .score((int) percentage).maxScore(100).percentage(percentage)
                .submittedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getResultDashboardRejectsUnknownAttempt() {
        when(attemptRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getResultDashboard(USER, "missing"));
    }

    @Test
    void getResultDashboardRejectsWrongOwner() {
        ExamAttempt a = attemptOf("a1", 80.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null);
        a.setUserId("someoneElse");
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        assertThrows(IllegalArgumentException.class, () -> service.getResultDashboard(USER, "a1"));
    }

    @Test
    void getResultDashboardOmitsRankBelowMinCohortSize() {
        ExamAttempt a = attemptOf("a1", 80.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null);
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        when(attemptRepository.findByCourseIdAndExamTypeAndDifficulty(COURSE, ExamType.PRACTICE, ExamDifficulty.BEGINNER))
                .thenReturn(List.of(a, attemptOf("a2", 50.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null)));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE)).thenReturn(List.of());

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertNull(dashboard.getRank());
    }

    @Test
    void getResultDashboardComputesPercentileWhenCohortLargeEnough() {
        ExamAttempt a = attemptOf("a1", 90.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null);
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        List<ExamAttempt> cohort = List.of(
                a,
                attemptOf("a2", 50.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null),
                attemptOf("a3", 60.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null),
                attemptOf("a4", 70.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null),
                attemptOf("a5", 40.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null));
        when(attemptRepository.findByCourseIdAndExamTypeAndDifficulty(COURSE, ExamType.PRACTICE, ExamDifficulty.BEGINNER))
                .thenReturn(cohort);
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE)).thenReturn(List.of());

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertNotNull(dashboard.getRank());
        assertEquals(5, dashboard.getRank().getCohortSize());
        assertEquals(100, dashboard.getRank().getPercentile()); // beat all 4 other attempts
        assertEquals(1, dashboard.getRank().getTopPercent()); // clamped — "Top 0%" would read oddly
    }

    @Test
    void getResultDashboardFocusAreasEmptyWhenNoTopicOrModuleAttempts() {
        ExamAttempt a = attemptOf("a1", 80.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null);
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE))
                .thenReturn(List.of(a)); // only a PRACTICE attempt on record

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertTrue(dashboard.getFocusAreas().isEmpty());
    }

    @Test
    void getResultDashboardAveragesPerTopicAndFlagsBelowPassingThreshold() {
        ExamAttempt a1 = attemptOf("a1", 90.0, ExamType.TOPIC_WISE, ExamDifficulty.BEGINNER, "Loops", null);
        ExamAttempt a2 = attemptOf("a2", 40.0, ExamType.TOPIC_WISE, ExamDifficulty.BEGINNER, "Recursion", null);
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a1));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE))
                .thenReturn(List.of(a1, a2));
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE)).thenReturn(List.of());

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertEquals(2, dashboard.getFocusAreas().size());
        // Sorted ascending — the lower-scoring "Recursion" area comes first.
        assertEquals("Recursion", dashboard.getFocusAreas().get(0).getLabel());
        assertEquals(40.0, dashboard.getFocusAreas().get(0).getAveragePercentage());
        assertTrue(dashboard.getFocusAreas().get(0).getFocusArea());
        assertEquals("Loops", dashboard.getFocusAreas().get(1).getLabel());
        assertFalse(dashboard.getFocusAreas().get(1).getFocusArea());
    }

    @Test
    void getResultDashboardResolvesAreaLinkFromStoredFkFields() {
        ExamAttempt a = attemptOf("a1", 40.0, ExamType.TOPIC_WISE, ExamDifficulty.BEGINNER, "Recursion", null);
        a.setCurriculumModuleId("mod-1");
        a.setSubmoduleId("sub-1");
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE)).thenReturn(List.of(a));
        CourseCurriculum.Submodule submodule = new CourseCurriculum.Submodule();
        submodule.setId("sub-1");
        submodule.setLabel("Recursion Basics"); // renamed since the attempt was taken
        CourseCurriculum module = CourseCurriculum.builder()
                .id("mod-1").moduleName("Advanced Python").submodules(List.of(submodule)).build();
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE)).thenReturn(List.of(module));

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertNotNull(dashboard.getFocusAreas().get(0).getLink());
        assertEquals("mod-1", dashboard.getFocusAreas().get(0).getLink().getCurriculumModuleId());
        assertEquals("sub-1", dashboard.getFocusAreas().get(0).getLink().getSubmoduleId());
        // Names reflect the CURRENT curriculum, not the (possibly stale) attempt-level label.
        assertEquals("Advanced Python", dashboard.getFocusAreas().get(0).getLink().getModuleName());
        assertEquals("Recursion Basics", dashboard.getFocusAreas().get(0).getLink().getSubmoduleLabel());
    }

    @Test
    void getResultDashboardIgnoresStaleFkWhenCurriculumNoLongerHasIt() {
        ExamAttempt a = attemptOf("a1", 40.0, ExamType.TOPIC_WISE, ExamDifficulty.BEGINNER, "Recursion", null);
        a.setCurriculumModuleId("deleted-mod");
        a.setSubmoduleId("deleted-sub");
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE)).thenReturn(List.of(a));
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE)).thenReturn(List.of()); // module was removed

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertNull(dashboard.getFocusAreas().get(0).getLink());
    }

    @Test
    void getResultDashboardModuleOnlyLinkLandsOnFirstSubmodule() {
        // A MODULE_WISE attempt only ever captures a module-level FK — no specific
        // submodule. The lecture player needs one concrete lesson to jump to, so the
        // link should resolve to the module's first (lowest-order, enabled) lesson.
        ExamAttempt a = attemptOf("a1", 40.0, ExamType.MODULE_WISE, ExamDifficulty.BEGINNER, null, "Advanced Python");
        a.setCurriculumModuleId("mod-1");
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE)).thenReturn(List.of(a));

        CourseCurriculum.Submodule secondLesson = new CourseCurriculum.Submodule();
        secondLesson.setId("sub-2");
        secondLesson.setLabel("Decorators");
        secondLesson.setOrder(2);
        secondLesson.setEnabled(true);
        CourseCurriculum.Submodule firstLesson = new CourseCurriculum.Submodule();
        firstLesson.setId("sub-1");
        firstLesson.setLabel("Intro to Advanced Python");
        firstLesson.setOrder(1);
        firstLesson.setEnabled(true);
        CourseCurriculum module = CourseCurriculum.builder()
                .id("mod-1").moduleName("Advanced Python")
                .submodules(List.of(secondLesson, firstLesson)) // out of order on purpose
                .build();
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE)).thenReturn(List.of(module));

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        AreaLinkDTO link = dashboard.getFocusAreas().get(0).getLink();
        assertEquals("sub-1", link.getSubmoduleId());
        assertEquals("Intro to Advanced Python", link.getSubmoduleLabel());
    }

    @Test
    void getResultDashboardResolvesAreaLinkByLabelMatchWhenNoFk() {
        ExamAttempt a = attemptOf("a1", 40.0, ExamType.TOPIC_WISE, ExamDifficulty.BEGINNER, "Recursion", null);
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE)).thenReturn(List.of(a));
        CourseCurriculum.Submodule submodule = new CourseCurriculum.Submodule();
        submodule.setId("sub-2");
        submodule.setLabel("Recursion");
        CourseCurriculum module = CourseCurriculum.builder().id("mod-2").submodules(List.of(submodule)).build();
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE)).thenReturn(List.of(module));

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertEquals("mod-2", dashboard.getFocusAreas().get(0).getLink().getCurriculumModuleId());
        assertEquals("sub-2", dashboard.getFocusAreas().get(0).getLink().getSubmoduleId());
    }

    @Test
    void getResultDashboardLinkIsNullWhenNothingMatches() {
        ExamAttempt a = attemptOf("a1", 40.0, ExamType.TOPIC_WISE, ExamDifficulty.BEGINNER, "Recursion", null);
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE)).thenReturn(List.of(a));
        when(curriculumRepository.findByCourseIdOrderByOrderAsc(COURSE)).thenReturn(List.of());

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertNull(dashboard.getFocusAreas().get(0).getLink());
    }

    @Test
    void getResultDashboardOffersHarderDifficultyOnlyWhenPassed() {
        ExamAttempt passed = attemptOf("a1", 90.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null);
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(passed));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE)).thenReturn(List.of());

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertTrue(dashboard.getRetakeOptions().getCanGoHarder());
        assertEquals(ExamDifficulty.INTERMEDIATE, dashboard.getRetakeOptions().getHarderDifficulty());
    }

    @Test
    void getResultDashboardStoredFeedbackPassesThroughUnmodified() {
        ExamAttempt a = attemptOf("a1", 80.0, ExamType.PRACTICE, ExamDifficulty.BEGINNER, null, null);
        a.setOverallFeedback("Great job overall.");
        a.setRecommendationText("Focus on edge cases next.");
        when(attemptRepository.findById("a1")).thenReturn(Optional.of(a));
        when(attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(USER, COURSE)).thenReturn(List.of());

        ExamResultDashboardDTO dashboard = service.getResultDashboard(USER, "a1");

        assertEquals("Great job overall.", dashboard.getOverallFeedback());
        assertEquals("Focus on edge cases next.", dashboard.getRecommendationText());
    }

    // ---------- submitAttempt AI feedback ----------

    @Test
    void submitAttemptStoresAiFeedbackAndNeverBlocksOnFailure() {
        ExamSession session = sessionFor("exam-1", USER, LocalDateTime.now());
        when(sessionRepository.findByExamSessionId("exam-1")).thenReturn(Optional.of(session));
        when(skillamaAiClient.getExamFeedback(anyString(), anyString(), anyInt(), anyInt(), any(Double.class), anyList()))
                .thenThrow(new RuntimeException("ai-tutor unavailable"));

        SubmitExamAttemptRequestDTO r = SubmitExamAttemptRequestDTO.builder()
                .examSessionId("exam-1").answers(Map.of("1", "A", "2", "B")).build();

        ExamAttemptResultDTO result = service.submitAttempt(USER, r);

        assertTrue(result.getPassed());
        assertEquals(ModuleQuizService.PASSING_PERCENTAGE, result.getPassingPercentage());
    }

    @Test
    void submitAttemptRecordsAiUsageWhenFeedbackSucceeds() {
        ExamSession session = sessionFor("exam-1", USER, LocalDateTime.now());
        when(sessionRepository.findByExamSessionId("exam-1")).thenReturn(Optional.of(session));
        when(skillamaAiClient.getExamFeedback(anyString(), anyString(), anyInt(), anyInt(), any(Double.class), anyList()))
                .thenReturn(ExamFeedbackResponseDTO.builder()
                        .overallFeedback("Nicely done.")
                        .recommendationText("Try harder questions next.")
                        .modelId("m").inputTokens(1).outputTokens(2).totalTokens(3)
                        .build());

        SubmitExamAttemptRequestDTO r = SubmitExamAttemptRequestDTO.builder()
                .examSessionId("exam-1").answers(Map.of("1", "A", "2", "B")).build();

        service.submitAttempt(USER, r);

        verify(attemptRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "Nicely done.".equals(saved.getOverallFeedback())
                        && "Try harder questions next.".equals(saved.getRecommendationText())));
    }
}
