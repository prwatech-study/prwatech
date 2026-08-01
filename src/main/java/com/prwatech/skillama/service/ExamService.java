package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AdminExamAttemptDTO;
import com.prwatech.skillama.dto.AdminExamRecommendationDTO;
import com.prwatech.skillama.dto.AiUsageRecordRequestDTO;
import com.prwatech.skillama.dto.ExamAnswerResultDTO;
import com.prwatech.skillama.dto.ExamAttemptResultDTO;
import com.prwatech.skillama.dto.ExamAttemptSummaryDTO;
import com.prwatech.skillama.dto.ExamRecommendationResponseDTO;
import com.prwatech.skillama.dto.GeneratedQuizDTO;
import com.prwatech.skillama.dto.ModuleQuizAttemptSummaryDTO;
import com.prwatech.skillama.dto.ModuleQuizOptionDTO;
import com.prwatech.skillama.dto.ModuleQuizQuestionDTO;
import com.prwatech.skillama.dto.StartExamRequestDTO;
import com.prwatech.skillama.dto.StartExamResponseDTO;
import com.prwatech.skillama.dto.SubmitExamAttemptRequestDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.ExamAttempt;
import com.prwatech.skillama.model.ExamDifficulty;
import com.prwatech.skillama.model.ExamRecommendationLog;
import com.prwatech.skillama.model.ExamSession;
import com.prwatech.skillama.model.ExamType;
import com.prwatech.skillama.model.User;
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
    private static final Map<ExamDifficulty, Integer> QUESTIONS_BY_DIFFICULTY = Map.of(
            ExamDifficulty.BEGINNER, 5,
            ExamDifficulty.INTERMEDIATE, 8,
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

        int numQuestions = QUESTIONS_BY_DIFFICULTY.getOrDefault(request.getDifficulty(), 5);
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
        LocalDateTime submittedAt = IndiaTime.now();
        Integer timeSpentSeconds = session.getStartedAt() != null
                ? (int) Duration.between(session.getStartedAt(), submittedAt).getSeconds()
                : null;
        Boolean overTimeLimit = session.getTimeLimitSeconds() != null && timeSpentSeconds != null
                && timeSpentSeconds > session.getTimeLimitSeconds();

        ExamAttempt attempt = ExamAttempt.builder()
                .userId(userId)
                .courseId(session.getCourseId())
                .moduleId(session.getModuleId())
                .topic(session.getTopic())
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
                .build();

        attempt = attemptRepository.save(attempt);

        return ExamAttemptResultDTO.builder()
                .attemptId(attempt.getId())
                .score(score)
                .maxScore(maxScore)
                .percentage(percentage)
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
