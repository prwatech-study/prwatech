package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AdminExamAttemptDTO;
import com.prwatech.skillama.dto.AdminExamRecommendationDTO;
import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.dto.ExamAnswerResultDTO;
import com.prwatech.skillama.dto.ExamAttemptResultDTO;
import com.prwatech.skillama.dto.ExamAttemptSummaryDTO;
import com.prwatech.skillama.dto.ExamFeedbackResponseDTO;
import com.prwatech.skillama.dto.ExamRecommendationResponseDTO;
import com.prwatech.skillama.dto.ExamResultDashboardDTO;
import com.prwatech.skillama.dto.FocusAreaDTO;
import com.prwatech.skillama.dto.RankStandingDTO;
import com.prwatech.skillama.dto.GeneratedQuizDTO;
import com.prwatech.skillama.dto.ModuleQuizAttemptSummaryDTO;
import com.prwatech.skillama.dto.ModuleQuizOptionDTO;
import com.prwatech.skillama.dto.ModuleQuizQuestionDTO;
import com.prwatech.skillama.dto.StartExamRequestDTO;
import com.prwatech.skillama.dto.StartExamResponseDTO;
import com.prwatech.skillama.dto.SubmitExamAttemptRequestDTO;
import com.prwatech.skillama.dto.AreaLinkDTO;
import com.prwatech.skillama.dto.RetakeOptionsDTO;
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
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI Exam — pure self-assessment (no course-progression side effects). Question
 * generation, the answer key, and the exam clock all follow the same hardened,
 * server-owned pattern established for Module Quiz (see {@link SkillamaAiClient}).
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private static final int SESSION_EXPIRY_HOURS = 2;
    private static final int SECONDS_PER_QUESTION = 90;
    private static final int MIN_QUESTIONS = 10;
    /** Below this cohort size a percentile would be based on too little data to be meaningful. */
    private static final int MIN_COHORT_SIZE = 5;
    private static final Map<ExamDifficulty, Integer> QUESTIONS_BY_DIFFICULTY = Map.of(
            ExamDifficulty.BEGINNER, 10,
            ExamDifficulty.INTERMEDIATE, 10,
            ExamDifficulty.ADVANCED, 10,
            ExamDifficulty.EXPERT, 12);

    private final ExamSessionRepository sessionRepository;
    private final ExamAttemptRepository attemptRepository;
    private final CourseRepository courseRepository;
    private final SkillamaAiClient skillamaAiClient;
    private final AiUsageService aiUsageService;
    private final SkillamaUserRepository userRepository;
    private final ModuleQuizService moduleQuizService;
    private final ExamRecommendationLogRepository recommendationLogRepository;
    private final CourseCurriculumRepository curriculumRepository;

    public StartExamResponseDTO startExam(String userId, StartExamRequestDTO request) {
        validateStartRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        aiUsageService.assertWithinBudget(user);

        String courseName = courseRepository.findById(request.getCourseId())
                .map(Course::getName)
                .orElse("this course");
        String topicHint = StringUtils.hasText(request.getModuleId())
                ? request.getModuleId()
                : StringUtils.hasText(request.getTopic()) ? request.getTopic() : courseName;
        List<String> topics = StringUtils.hasText(request.getTopic())
                ? List.of(request.getTopic())
                : new ArrayList<>();

        int numQuestions = QUESTIONS_BY_DIFFICULTY.getOrDefault(request.getDifficulty(), MIN_QUESTIONS);
        int timeLimitSeconds = numQuestions * SECONDS_PER_QUESTION;

        GeneratedQuizDTO generated = skillamaAiClient.generateQuizQuestions(
                courseName, topicHint, topics, numQuestions,
                request.getDifficulty() != null ? request.getDifficulty().name().toLowerCase() : null);
        if (generated.getQuestions() == null || generated.getQuestions().isEmpty()) {
            throw new IllegalStateException("AI did not return any exam questions");
        }

        String examSessionId = "exam-" + UUID.randomUUID();
        LocalDateTime now = IndiaTime.now();

        List<ExamSession.ExamQuestion> questions = generated.getQuestions().stream()
                .map(this::toSessionQuestion)
                .collect(Collectors.toList());

        ExamSession session = ExamSession.builder()
                .examSessionId(examSessionId)
                .userId(userId)
                .courseId(request.getCourseId())
                .moduleId(request.getModuleId())
                .topic(request.getTopic())
                .curriculumModuleId(request.getCurriculumModuleId())
                .submoduleId(request.getSubmoduleId())
                .difficulty(request.getDifficulty())
                .examType(request.getExamType())
                .examTitle(StringUtils.hasText(generated.getQuizTitle())
                        ? generated.getQuizTitle()
                        : "AI Exam: " + topicHint)
                .questions(questions)
                .createdAt(now)
                .startedAt(now)
                .timeLimitSeconds(timeLimitSeconds)
                .expiresAt(now.plusHours(SESSION_EXPIRY_HOURS))
                .build();

        sessionRepository.save(session);

        aiUsageService.recordUsage(AiUsageRecordRequestDTO.builder()
                .userId(userId)
                .endpoint("generate_exam")
                .courseId(request.getCourseId())
                .modelId(generated.getModelId())
                .inputTokens(generated.getInputTokens())
                .outputTokens(generated.getOutputTokens())
                .totalTokens(generated.getTotalTokens())
                .build());

        List<ModuleQuizQuestionDTO> clientQuestions = questions.stream()
                .map(this::toClientQuestion)
                .collect(Collectors.toList());

        return StartExamResponseDTO.builder()
                .examSessionId(examSessionId)
                .examTitle(session.getExamTitle())
                .questions(clientQuestions)
                .totalQuestions(clientQuestions.size())
                .timeLimitSeconds(timeLimitSeconds)
                .difficulty(request.getDifficulty())
                .examType(request.getExamType())
                .build();
    }

    public ExamAttemptResultDTO submitAttempt(String userId, SubmitExamAttemptRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getExamSessionId())) {
            throw new IllegalArgumentException("examSessionId is required");
        }
        if (request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new IllegalArgumentException("answers are required");
        }

        ExamSession session = sessionRepository.findByExamSessionId(request.getExamSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Exam session not found"));

        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(IndiaTime.now())) {
            throw new IllegalArgumentException("Exam session has expired");
        }
        if (!userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("Exam session does not belong to this user");
        }

        List<ExamAttempt.AnswerRecord> answerRecords = new ArrayList<>();
        List<ExamAnswerResultDTO> answerResults = new ArrayList<>();
        int score = 0;
        int maxScore = session.getQuestions().size();

        for (ExamSession.ExamQuestion question : session.getQuestions()) {
            String questionKey = String.valueOf(question.getId());
            String selectedKey = request.getAnswers().get(questionKey);
            boolean isCorrect = question.getCorrectKey() != null
                    && question.getCorrectKey().equalsIgnoreCase(selectedKey);
            if (isCorrect) {
                score++;
            }

            answerRecords.add(ExamAttempt.AnswerRecord.builder()
                    .questionId(question.getId())
                    .questionText(question.getQuestion())
                    .selectedKey(selectedKey)
                    .correctKey(question.getCorrectKey())
                    .isCorrect(isCorrect)
                    .explanation(question.getExplanation())
                    .options(question.getOptions())
                    .build());

            answerResults.add(ExamAnswerResultDTO.builder()
                    .questionId(question.getId())
                    .questionText(question.getQuestion())
                    .selectedKey(selectedKey)
                    .selectedOptionText(resolveOptionText(question.getOptions(), selectedKey))
                    .correctKey(question.getCorrectKey())
                    .correctOptionText(resolveOptionText(question.getOptions(), question.getCorrectKey()))
                    .isCorrect(isCorrect)
                    .explanation(question.getExplanation())
                    .options(toOptionDtos(question.getOptions()))
                    .build());
        }

        double percentage = maxScore > 0 ? (score * 100.0) / maxScore : 0.0;
        boolean passed = percentage >= ModuleQuizService.PASSING_PERCENTAGE;
        LocalDateTime submittedAt = IndiaTime.now();
        Integer timeSpentSeconds = session.getStartedAt() != null
                ? (int) Duration.between(session.getStartedAt(), submittedAt).getSeconds()
                : null;
        Boolean overTimeLimit = session.getTimeLimitSeconds() != null && timeSpentSeconds != null
                && timeSpentSeconds > session.getTimeLimitSeconds();

        List<String> wrongTopics = answerRecords.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsCorrect()))
                .map(a -> StringUtils.hasText(session.getTopic()) ? session.getTopic() : session.getModuleId())
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        String courseName = courseRepository.findById(session.getCourseId())
                .map(Course::getName)
                .orElse("this course");
        String topicOrModule = StringUtils.hasText(session.getTopic())
                ? session.getTopic()
                : StringUtils.hasText(session.getModuleId()) ? session.getModuleId() : courseName;

        ExamFeedbackResponseDTO feedback;
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                aiUsageService.assertWithinBudget(user);
            }
            feedback = skillamaAiClient.getExamFeedback(
                    courseName, topicOrModule, score, maxScore, percentage, wrongTopics);
            aiUsageService.recordUsage(AiUsageRecordRequestDTO.builder()
                    .userId(userId)
                    .endpoint("ai_exam_feedback")
                    .courseId(session.getCourseId())
                    .modelId(feedback.getModelId())
                    .inputTokens(feedback.getInputTokens())
                    .outputTokens(feedback.getOutputTokens())
                    .totalTokens(feedback.getTotalTokens())
                    .build());
        } catch (Exception e) {
            // Best-effort only — feedback must never block a submission from being graded/saved.
            feedback = ExamFeedbackResponseDTO.builder()
                    .overallFeedback("You scored " + Math.round(percentage) + "%.")
                    .recommendationText("Review the topics you missed and try again.")
                    .build();
        }

        ExamAttempt attempt = ExamAttempt.builder()
                .userId(userId)
                .courseId(session.getCourseId())
                .moduleId(session.getModuleId())
                .topic(session.getTopic())
                .curriculumModuleId(session.getCurriculumModuleId())
                .submoduleId(session.getSubmoduleId())
                .difficulty(session.getDifficulty())
                .examType(session.getExamType())
                .examSessionId(session.getExamSessionId())
                .score(score)
                .maxScore(maxScore)
                .percentage(percentage)
                .answers(answerRecords)
                .timeSpentSeconds(timeSpentSeconds)
                .overTimeLimit(overTimeLimit)
                .submittedAt(submittedAt)
                .overallFeedback(feedback.getOverallFeedback())
                .recommendationText(feedback.getRecommendationText())
                .build();

        attempt = attemptRepository.save(attempt);

        return ExamAttemptResultDTO.builder()
                .attemptId(attempt.getId())
                .score(score)
                .maxScore(maxScore)
                .percentage(percentage)
                .passed(passed)
                .passingPercentage(ModuleQuizService.PASSING_PERCENTAGE)
                .timeSpentSeconds(timeSpentSeconds)
                .overTimeLimit(overTimeLimit)
                .answers(answerResults)
                .build();
    }

    public List<ExamAttemptSummaryDTO> listMyAttempts(String userId, String courseId) {
        List<ExamAttempt> attempts = StringUtils.hasText(courseId)
                ? attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(userId, courseId)
                : attemptRepository.findByUserIdOrderBySubmittedAtDesc(userId);
        return attempts.stream().map(this::toSummary).collect(Collectors.toList());
    }

    /**
     * Best-effort recommendation using data we already have (course completion,
     * average Module Quiz score) fed into a dedicated LLM call. See
     * {@link SkillamaAiClient#getExamRecommendation} for why every field has a
     * safe fallback.
     */
    public ExamRecommendationResponseDTO getRecommendation(String userId, String courseId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        aiUsageService.assertWithinBudget(user);

        String courseName = courseRepository.findById(courseId).map(Course::getName).orElse("this course");

        List<ModuleQuizAttemptSummaryDTO> quizAttempts = moduleQuizService.getAttempts(null, userId, courseId, null);
        Double avgQuizScore = quizAttempts.isEmpty()
                ? null
                : quizAttempts.stream().mapToDouble(ModuleQuizAttemptSummaryDTO::getPercentage).average().orElse(0.0);

        ExamRecommendationResponseDTO recommendation =
                skillamaAiClient.getExamRecommendation(courseName, null, avgQuizScore);

        aiUsageService.recordUsage(AiUsageRecordRequestDTO.builder()
                .userId(userId)
                .endpoint("ai_exam_recommendation")
                .courseId(courseId)
                .modelId(recommendation.getModelId())
                .inputTokens(recommendation.getInputTokens())
                .outputTokens(recommendation.getOutputTokens())
                .totalTokens(recommendation.getTotalTokens())
                .build());

        // Persisted so a recommendation can be reviewed after the fact, not just billed.
        recommendationLogRepository.save(ExamRecommendationLog.builder()
                .userId(userId)
                .courseId(courseId)
                .difficulty(recommendation.getDifficulty())
                .topic(recommendation.getTopic())
                .reasoning(recommendation.getReasoning())
                .estimatedMinutes(recommendation.getEstimatedMinutes())
                .expectedScorePercent(recommendation.getExpectedScorePercent())
                .modelId(recommendation.getModelId())
                .createdAt(IndiaTime.now())
                .build());

        return recommendation;
    }

    /**
     * Full learner-facing Result Dashboard for one attempt — rank and focus areas
     * are computed fresh on every call (not cached from submission time) so they
     * stay current as more attempts accumulate.
     */
    public ExamResultDashboardDTO getResultDashboard(String userId, String attemptId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Exam attempt not found"));
        if (!userId.equals(attempt.getUserId())) {
            throw new IllegalArgumentException("Exam attempt does not belong to this user");
        }

        String courseName = attempt.getCourseId() != null
                ? courseRepository.findById(attempt.getCourseId()).map(Course::getName).orElse(null)
                : null;
        List<ExamAnswerResultDTO> answers = attempt.getAnswers() == null
                ? new ArrayList<>()
                : attempt.getAnswers().stream().map(this::toAnswerResult).collect(Collectors.toList());
        boolean passed = attempt.getPercentage() != null
                && attempt.getPercentage() >= ModuleQuizService.PASSING_PERCENTAGE;

        return ExamResultDashboardDTO.builder()
                .attemptId(attempt.getId())
                .courseId(attempt.getCourseId())
                .courseName(courseName)
                .moduleId(attempt.getModuleId())
                .topic(attempt.getTopic())
                .difficulty(attempt.getDifficulty())
                .examType(attempt.getExamType())
                .score(attempt.getScore())
                .maxScore(attempt.getMaxScore())
                .percentage(attempt.getPercentage())
                .passed(passed)
                .passingPercentage(ModuleQuizService.PASSING_PERCENTAGE)
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .overTimeLimit(attempt.getOverTimeLimit())
                .submittedAt(attempt.getSubmittedAt())
                .answers(answers)
                .overallFeedback(attempt.getOverallFeedback())
                .recommendationText(attempt.getRecommendationText())
                .rank(computeCohortStanding(attempt))
                .focusAreas(computeFocusAreas(userId, attempt.getCourseId()))
                .retakeOptions(buildRetakeOptions(attempt))
                .build();
    }

    /**
     * Percentile standing among every attempt sharing this attempt's courseId +
     * examType + difficulty. Returns null below {@link #MIN_COHORT_SIZE} — a
     * percentile off a handful of attempts would be noise, not a rank.
     */
    private RankStandingDTO computeCohortStanding(ExamAttempt attempt) {
        if (attempt.getCourseId() == null || attempt.getExamType() == null || attempt.getDifficulty() == null) {
            return null;
        }
        List<ExamAttempt> cohort = attemptRepository.findByCourseIdAndExamTypeAndDifficulty(
                attempt.getCourseId(), attempt.getExamType(), attempt.getDifficulty());
        int cohortSize = cohort.size();
        if (cohortSize < MIN_COHORT_SIZE) {
            return null;
        }

        double thisPercentage = attempt.getPercentage() != null ? attempt.getPercentage() : 0.0;
        long scoredBelow = cohort.stream()
                .filter(a -> !a.getId().equals(attempt.getId()))
                .filter(a -> a.getPercentage() != null && a.getPercentage() < thisPercentage)
                .count();
        int percentile = (int) Math.round((scoredBelow * 100.0) / (cohortSize - 1));
        // Clamp to 1 for display — "Top 0%" reads oddly even for the best score in the cohort.
        int topPercent = Math.max(1, 100 - percentile);

        return RankStandingDTO.builder()
                .percentile(percentile)
                .topPercent(topPercent)
                .cohortSize(cohortSize)
                .build();
    }

    /**
     * Averages the learner's TOPIC_WISE/MODULE_WISE attempts per topic/module for
     * this course. PRACTICE/AI_RECOMMENDED attempts aren't included — there's no
     * per-question topic tagging on them, so a breakdown would be fabricated.
     * Empty when the learner hasn't taken any exam of those two types yet.
     */
    private List<FocusAreaDTO> computeFocusAreas(String userId, String courseId) {
        if (courseId == null) {
            return new ArrayList<>();
        }
        List<ExamAttempt> areaAttempts = attemptRepository.findByUserIdAndCourseIdOrderBySubmittedAtDesc(userId, courseId)
                .stream()
                .filter(a -> a.getExamType() == ExamType.TOPIC_WISE || a.getExamType() == ExamType.MODULE_WISE)
                .filter(a -> StringUtils.hasText(a.getTopic()) || StringUtils.hasText(a.getModuleId()))
                .collect(Collectors.toList());
        if (areaAttempts.isEmpty()) {
            return new ArrayList<>();
        }

        List<CourseCurriculum> curriculum = curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
        Map<String, List<ExamAttempt>> byArea = areaAttempts.stream()
                .collect(Collectors.groupingBy(
                        a -> StringUtils.hasText(a.getTopic()) ? a.getTopic() : a.getModuleId()));

        return byArea.entrySet().stream()
                .map(entry -> {
                    String label = entry.getKey();
                    List<ExamAttempt> attemptsForArea = entry.getValue();
                    double average = attemptsForArea.stream()
                            .filter(a -> a.getPercentage() != null)
                            .mapToDouble(ExamAttempt::getPercentage)
                            .average()
                            .orElse(0.0);
                    return FocusAreaDTO.builder()
                            .label(label)
                            .averagePercentage(average)
                            .attemptCount(attemptsForArea.size())
                            .focusArea(average < ModuleQuizService.PASSING_PERCENTAGE)
                            // Most recent attempt for this area is index 0 — findByUserIdAndCourseId...Desc is sorted.
                            .link(resolveAreaLink(courseId, attemptsForArea.get(0), label, curriculum))
                            .build();
                })
                .sorted(Comparator.comparingDouble(FocusAreaDTO::getAveragePercentage))
                .collect(Collectors.toList());
    }

    /**
     * Prefers the attempt's stored curriculum FK, but only trusts it once it's
     * confirmed against the CURRENT curriculum (so a deleted/renamed module never
     * produces a stale link) — falling through to a best-effort case-insensitive
     * label match otherwise (also self-heals attempts made before the FK fields
     * existed). Returns the module/submodule's current display name alongside the
     * ids, since the frontend's lecture player resolves by name, not id. Null —
     * never a guess — when nothing matches.
     */
    private AreaLinkDTO resolveAreaLink(
            String courseId, ExamAttempt attempt, String label, List<CourseCurriculum> curriculum) {
        if (curriculum == null) {
            return null;
        }
        if (StringUtils.hasText(attempt.getCurriculumModuleId())) {
            for (CourseCurriculum module : curriculum) {
                if (!attempt.getCurriculumModuleId().equals(module.getId())) {
                    continue;
                }
                if (StringUtils.hasText(attempt.getSubmoduleId()) && module.getSubmodules() != null) {
                    for (CourseCurriculum.Submodule submodule : module.getSubmodules()) {
                        if (attempt.getSubmoduleId().equals(submodule.getId())) {
                            return AreaLinkDTO.builder()
                                    .courseId(courseId)
                                    .curriculumModuleId(module.getId())
                                    .submoduleId(submodule.getId())
                                    .moduleName(module.getModuleName())
                                    .submoduleLabel(submodule.getLabel())
                                    .build();
                        }
                    }
                }
                return moduleLink(courseId, module);
            }
        }

        if (!StringUtils.hasText(label)) {
            return null;
        }
        for (CourseCurriculum module : curriculum) {
            if (module.getSubmodules() != null) {
                for (CourseCurriculum.Submodule submodule : module.getSubmodules()) {
                    if (submodule.getLabel() != null && submodule.getLabel().equalsIgnoreCase(label)) {
                        return AreaLinkDTO.builder()
                                .courseId(courseId)
                                .curriculumModuleId(module.getId())
                                .submoduleId(submodule.getId())
                                .moduleName(module.getModuleName())
                                .submoduleLabel(submodule.getLabel())
                                .build();
                    }
                }
            }
            if (module.getModuleName() != null && module.getModuleName().equalsIgnoreCase(label)) {
                return moduleLink(courseId, module);
            }
        }
        return null;
    }

    /**
     * A module-level match with no specific submodule lands on the module's first
     * lesson — the frontend's lecture player always jumps to one specific lesson,
     * not "a module" in the abstract.
     */
    private AreaLinkDTO moduleLink(String courseId, CourseCurriculum module) {
        CourseCurriculum.Submodule first = firstSubmodule(module);
        return AreaLinkDTO.builder()
                .courseId(courseId)
                .curriculumModuleId(module.getId())
                .submoduleId(first != null ? first.getId() : null)
                .moduleName(module.getModuleName())
                .submoduleLabel(first != null ? first.getLabel() : null)
                .build();
    }

    private CourseCurriculum.Submodule firstSubmodule(CourseCurriculum module) {
        if (module.getSubmodules() == null) {
            return null;
        }
        return module.getSubmodules().stream()
                .filter(s -> s.getEnabled() == null || s.getEnabled())
                .min(Comparator.comparing(s -> s.getOrder() != null ? s.getOrder() : Integer.MAX_VALUE))
                .orElse(null);
    }

    private RetakeOptionsDTO buildRetakeOptions(ExamAttempt attempt) {
        boolean passed = attempt.getPercentage() != null
                && attempt.getPercentage() >= ModuleQuizService.PASSING_PERCENTAGE;
        ExamDifficulty harder = passed ? nextDifficulty(attempt.getDifficulty()) : null;
        return RetakeOptionsDTO.builder()
                .sameExamType(attempt.getExamType())
                .sameDifficulty(attempt.getDifficulty())
                .sameModuleId(attempt.getModuleId())
                .sameTopic(attempt.getTopic())
                .canGoHarder(harder != null)
                .harderDifficulty(harder)
                .build();
    }

    private ExamDifficulty nextDifficulty(ExamDifficulty current) {
        if (current == null) {
            return null;
        }
        switch (current) {
            case BEGINNER:
                return ExamDifficulty.INTERMEDIATE;
            case INTERMEDIATE:
                return ExamDifficulty.ADVANCED;
            case ADVANCED:
                return ExamDifficulty.EXPERT;
            default:
                return null;
        }
    }

    private ExamAnswerResultDTO toAnswerResult(ExamAttempt.AnswerRecord record) {
        return ExamAnswerResultDTO.builder()
                .questionId(record.getQuestionId())
                .questionText(record.getQuestionText())
                .selectedKey(record.getSelectedKey())
                .selectedOptionText(resolveOptionText(record.getOptions(), record.getSelectedKey()))
                .correctKey(record.getCorrectKey())
                .correctOptionText(resolveOptionText(record.getOptions(), record.getCorrectKey()))
                .isCorrect(record.getIsCorrect())
                .explanation(record.getExplanation())
                .options(toOptionDtos(record.getOptions()))
                .build();
    }

    /** Admin monitor: paginated AI Exam attempts across all learners. */
    public Page<AdminExamAttemptDTO> listAdminAttempts(
            int page, int size, String userId, String courseId, String email) {
        int limit = Math.min(Math.max(size, 1), 100);
        int pageNum = Math.max(page, 0);

        String emailFilter = email != null ? email.trim().toLowerCase() : null;
        Set<String> allowedUserIds = null;
        if (emailFilter != null && !emailFilter.isBlank()) {
            allowedUserIds = userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().toLowerCase().contains(emailFilter))
                    .map(User::getId)
                    .collect(Collectors.toSet());
            if (allowedUserIds.isEmpty()) {
                return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), 0);
            }
        }

        Map<String, User> userCache = new HashMap<>();
        Map<String, String> courseNameCache = new HashMap<>();
        Set<String> allowedUserIdsFinal = allowedUserIds;

        List<AdminExamAttemptDTO> rows = attemptRepository.findAll().stream()
                .filter(a -> userId == null || userId.isBlank() || userId.equals(a.getUserId()))
                .filter(a -> courseId == null || courseId.isBlank() || courseId.equals(a.getCourseId()))
                .filter(a -> allowedUserIdsFinal == null
                        || (a.getUserId() != null && allowedUserIdsFinal.contains(a.getUserId())))
                .map(a -> {
                    User user = a.getUserId() != null
                            ? userCache.computeIfAbsent(a.getUserId(), id -> userRepository.findById(id).orElse(null))
                            : null;
                    String courseName = a.getCourseId() != null
                            ? courseNameCache.computeIfAbsent(a.getCourseId(),
                                    cid -> courseRepository.findById(cid).map(Course::getName).orElse(null))
                            : null;
                    return AdminExamAttemptDTO.builder()
                            .attemptId(a.getId())
                            .userId(a.getUserId())
                            .userName(user != null ? user.getName() : null)
                            .userEmail(user != null ? user.getEmail() : null)
                            .courseId(a.getCourseId())
                            .courseName(courseName)
                            .moduleId(a.getModuleId())
                            .topic(a.getTopic())
                            .difficulty(a.getDifficulty())
                            .examType(a.getExamType())
                            .score(a.getScore())
                            .maxScore(a.getMaxScore())
                            .percentage(a.getPercentage())
                            .timeSpentSeconds(a.getTimeSpentSeconds())
                            .overTimeLimit(a.getOverTimeLimit())
                            .submittedAt(a.getSubmittedAt())
                            .build();
                })
                .collect(Collectors.toList());

        rows.sort(Comparator.comparing(
                AdminExamAttemptDTO::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int total = rows.size();
        int from = pageNum * limit;
        if (from >= total) {
            return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), total);
        }
        int to = Math.min(from + limit, total);
        return new PageImpl<>(rows.subList(from, to), PageRequest.of(pageNum, limit), total);
    }

    /** Admin monitor: paginated "AI Recommended Test" suggestions across all learners. */
    public Page<AdminExamRecommendationDTO> listAdminRecommendations(
            int page, int size, String userId, String courseId, String email) {
        int limit = Math.min(Math.max(size, 1), 100);
        int pageNum = Math.max(page, 0);

        String emailFilter = email != null ? email.trim().toLowerCase() : null;
        Set<String> allowedUserIds = null;
        if (emailFilter != null && !emailFilter.isBlank()) {
            allowedUserIds = userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().toLowerCase().contains(emailFilter))
                    .map(User::getId)
                    .collect(Collectors.toSet());
            if (allowedUserIds.isEmpty()) {
                return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), 0);
            }
        }

        Map<String, User> userCache = new HashMap<>();
        Map<String, String> courseNameCache = new HashMap<>();
        Set<String> allowedUserIdsFinal = allowedUserIds;

        List<AdminExamRecommendationDTO> rows = recommendationLogRepository.findAll().stream()
                .filter(r -> userId == null || userId.isBlank() || userId.equals(r.getUserId()))
                .filter(r -> courseId == null || courseId.isBlank() || courseId.equals(r.getCourseId()))
                .filter(r -> allowedUserIdsFinal == null
                        || (r.getUserId() != null && allowedUserIdsFinal.contains(r.getUserId())))
                .map(r -> {
                    User user = r.getUserId() != null
                            ? userCache.computeIfAbsent(r.getUserId(), id -> userRepository.findById(id).orElse(null))
                            : null;
                    String courseName = r.getCourseId() != null
                            ? courseNameCache.computeIfAbsent(r.getCourseId(),
                                    cid -> courseRepository.findById(cid).map(Course::getName).orElse(null))
                            : null;
                    return AdminExamRecommendationDTO.builder()
                            .id(r.getId())
                            .userId(r.getUserId())
                            .userName(user != null ? user.getName() : null)
                            .userEmail(user != null ? user.getEmail() : null)
                            .courseId(r.getCourseId())
                            .courseName(courseName)
                            .difficulty(r.getDifficulty())
                            .topic(r.getTopic())
                            .reasoning(r.getReasoning())
                            .estimatedMinutes(r.getEstimatedMinutes())
                            .expectedScorePercent(r.getExpectedScorePercent())
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        rows.sort(Comparator.comparing(
                AdminExamRecommendationDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int total = rows.size();
        int from = pageNum * limit;
        if (from >= total) {
            return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), total);
        }
        int to = Math.min(from + limit, total);
        return new PageImpl<>(rows.subList(from, to), PageRequest.of(pageNum, limit), total);
    }

    private void validateStartRequest(StartExamRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!StringUtils.hasText(request.getCourseId())) {
            throw new IllegalArgumentException("courseId is required");
        }
        if (request.getDifficulty() == null) {
            throw new IllegalArgumentException("difficulty is required");
        }
        if (request.getExamType() == null) {
            throw new IllegalArgumentException("examType is required");
        }
        if (request.getExamType() == ExamType.MODULE_WISE && !StringUtils.hasText(request.getModuleId())) {
            throw new IllegalArgumentException("moduleId is required for a module-wise exam");
        }
        if (request.getExamType() == ExamType.TOPIC_WISE && !StringUtils.hasText(request.getTopic())) {
            throw new IllegalArgumentException("topic is required for a topic-wise exam");
        }
    }

    private ExamSession.ExamQuestion toSessionQuestion(ModuleQuizQuestionDTO dto) {
        List<ExamSession.ExamOption> options = dto.getOptions() == null
                ? new ArrayList<>()
                : dto.getOptions().stream()
                        .map(o -> ExamSession.ExamOption.builder().key(o.getKey()).text(o.getText()).build())
                        .collect(Collectors.toList());

        return ExamSession.ExamQuestion.builder()
                .id(dto.getId())
                .question(dto.getQuestion())
                .options(options)
                .correctKey(dto.getCorrectKey())
                .explanation(dto.getExplanation())
                .build();
    }

    private ModuleQuizQuestionDTO toClientQuestion(ExamSession.ExamQuestion q) {
        List<ModuleQuizOptionDTO> options = q.getOptions() == null
                ? new ArrayList<>()
                : q.getOptions().stream()
                        .map(o -> ModuleQuizOptionDTO.builder().key(o.getKey()).text(o.getText()).build())
                        .collect(Collectors.toList());

        return ModuleQuizQuestionDTO.builder()
                .id(q.getId())
                .question(q.getQuestion())
                .options(options)
                .build();
    }

    private List<ModuleQuizOptionDTO> toOptionDtos(List<ExamSession.ExamOption> options) {
        if (options == null) {
            return new ArrayList<>();
        }
        return options.stream()
                .map(o -> ModuleQuizOptionDTO.builder().key(o.getKey()).text(o.getText()).build())
                .collect(Collectors.toList());
    }

    private String resolveOptionText(List<ExamSession.ExamOption> options, String key) {
        if (!StringUtils.hasText(key) || options == null) {
            return null;
        }
        return options.stream()
                .filter(o -> key.equalsIgnoreCase(o.getKey()))
                .map(ExamSession.ExamOption::getText)
                .findFirst()
                .orElse(null);
    }

    private ExamAttemptSummaryDTO toSummary(ExamAttempt attempt) {
        return ExamAttemptSummaryDTO.builder()
                .attemptId(attempt.getId())
                .courseId(attempt.getCourseId())
                .moduleId(attempt.getModuleId())
                .topic(attempt.getTopic())
                .difficulty(attempt.getDifficulty())
                .examType(attempt.getExamType())
                .score(attempt.getScore())
                .maxScore(attempt.getMaxScore())
                .percentage(attempt.getPercentage())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .overTimeLimit(attempt.getOverTimeLimit())
                .submittedAt(attempt.getSubmittedAt())
                .build();
    }
}
