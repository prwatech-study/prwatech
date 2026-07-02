package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.model.ModuleQuizAttempt;
import com.prwatech.skillama.model.ModuleQuizSession;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.ModuleQuizAttemptRepository;
import com.prwatech.skillama.repository.ModuleQuizSessionRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleQuizService {

    public static final int PASSING_PERCENTAGE = 70;
    private static final int SESSION_EXPIRY_HOURS = 2;

    private final ModuleQuizSessionRepository sessionRepository;
    private final ModuleQuizAttemptRepository attemptRepository;
    private final UserProfileRepository userProfileRepository;

    public CreateModuleQuizSessionResponseDTO createSession(
            String profilingSessionId, String userId, CreateModuleQuizSessionRequestDTO request) {

        validateSessionRequest(request);

        String quizSessionId = "quiz-" + UUID.randomUUID();
        LocalDateTime now = IndiaTime.now();

        List<ModuleQuizSession.QuizQuestion> questions = request.getQuestions().stream()
                .map(this::toSessionQuestion)
                .collect(Collectors.toList());

        ModuleQuizSession session = ModuleQuizSession.builder()
                .quizSessionId(quizSessionId)
                .userId(userId)
                .guestSessionId(userId == null ? profilingSessionId : null)
                .courseId(request.getCourseId())
                .moduleName(request.getModuleName())
                .quizTitle(StringUtils.hasText(request.getQuizTitle())
                        ? request.getQuizTitle()
                        : "Module Quiz: " + request.getModuleName())
                .questions(questions)
                .createdAt(now)
                .expiresAt(now.plusHours(SESSION_EXPIRY_HOURS))
                .build();

        sessionRepository.save(session);

        List<ModuleQuizQuestionDTO> clientQuestions = questions.stream()
                .map(this::toClientQuestion)
                .collect(Collectors.toList());

        return CreateModuleQuizSessionResponseDTO.builder()
                .sessionId(quizSessionId)
                .quizTitle(session.getQuizTitle())
                .questions(clientQuestions)
                .totalQuestions(clientQuestions.size())
                .build();
    }

    public ModuleQuizAttemptResultDTO submitAttempt(
            String profilingSessionId, String userId, SubmitModuleQuizAttemptRequestDTO request) {

        if (request == null || !StringUtils.hasText(request.getSessionId())) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new IllegalArgumentException("answers are required");
        }

        ModuleQuizSession session = sessionRepository.findByQuizSessionId(request.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Quiz session not found"));

        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(IndiaTime.now())) {
            throw new IllegalArgumentException("Quiz session has expired");
        }

        assertSessionOwnership(session, profilingSessionId, userId);

        int attemptNumber = countAttempts(userId, profilingSessionId, session.getCourseId(), session.getModuleName()) + 1;

        List<ModuleQuizAttempt.AnswerRecord> answerRecords = new ArrayList<>();
        List<ModuleQuizAnswerResultDTO> answerResults = new ArrayList<>();
        int score = 0;
        int maxScore = session.getQuestions().size();

        for (ModuleQuizSession.QuizQuestion question : session.getQuestions()) {
            String questionKey = String.valueOf(question.getId());
            String selectedKey = request.getAnswers().get(questionKey);
            if (selectedKey == null) {
                selectedKey = request.getAnswers().get(questionKey + "");
            }
            boolean isCorrect = question.getCorrectKey() != null
                    && question.getCorrectKey().equalsIgnoreCase(selectedKey);
            if (isCorrect) {
                score++;
            }

            answerRecords.add(ModuleQuizAttempt.AnswerRecord.builder()
                    .questionId(question.getId())
                    .questionText(question.getQuestion())
                    .selectedKey(selectedKey)
                    .correctKey(question.getCorrectKey())
                    .isCorrect(isCorrect)
                    .options(question.getOptions())
                    .build());

            answerResults.add(buildAnswerResult(question, selectedKey, isCorrect));
        }

        double percentage = maxScore > 0 ? (score * 100.0) / maxScore : 0.0;
        boolean passed = percentage >= PASSING_PERCENTAGE;

        ModuleQuizAttempt attempt = ModuleQuizAttempt.builder()
                .userId(userId)
                .guestSessionId(userId == null ? profilingSessionId : null)
                .courseId(session.getCourseId())
                .moduleName(session.getModuleName())
                .quizSessionId(session.getQuizSessionId())
                .attemptNumber(attemptNumber)
                .score(score)
                .maxScore(maxScore)
                .percentage(percentage)
                .passed(passed)
                .answers(answerRecords)
                .timeSpentSeconds(request.getTimeSpentSeconds())
                .submittedAt(IndiaTime.now())
                .build();

        attempt = attemptRepository.save(attempt);

        if (passed) {
            recordPassedQuiz(profilingSessionId, userId, session.getCourseId(), session.getModuleName(), score, maxScore);
        }

        return ModuleQuizAttemptResultDTO.builder()
                .attemptId(attempt.getId())
                .attemptNumber(attemptNumber)
                .score(score)
                .maxScore(maxScore)
                .percentage(percentage)
                .passed(passed)
                .passingPercentage(PASSING_PERCENTAGE)
                .answers(answerResults)
                .build();
    }

    public List<ModuleQuizAttemptSummaryDTO> getAttempts(
            String profilingSessionId, String userId, String courseId, String moduleName) {

        List<ModuleQuizAttempt> attempts;
        if (StringUtils.hasText(userId)) {
            attempts = StringUtils.hasText(moduleName)
                    ? attemptRepository.findByUserIdAndCourseIdAndModuleNameOrderBySubmittedAtDesc(
                            userId, courseId, moduleName)
                    : attemptRepository.findAll().stream()
                            .filter(a -> userId.equals(a.getUserId()) && courseId.equals(a.getCourseId()))
                            .sorted(Comparator.comparing(ModuleQuizAttempt::getSubmittedAt).reversed())
                            .collect(Collectors.toList());
        } else {
            attempts = StringUtils.hasText(moduleName)
                    ? attemptRepository.findByGuestSessionIdAndCourseIdAndModuleNameOrderBySubmittedAtDesc(
                            profilingSessionId, courseId, moduleName)
                    : attemptRepository.findAll().stream()
                            .filter(a -> profilingSessionId.equals(a.getGuestSessionId())
                                    && courseId.equals(a.getCourseId()))
                            .sorted(Comparator.comparing(ModuleQuizAttempt::getSubmittedAt).reversed())
                            .collect(Collectors.toList());
        }

        return attempts.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public boolean hasPassedModuleQuiz(UserProfile profile, String courseId, String moduleName) {
        if (profile.getPassedModuleQuizzes() == null) {
            return false;
        }
        return profile.getPassedModuleQuizzes().stream()
                .anyMatch(p -> courseId.equals(p.getCourseId()) && moduleName.equals(p.getModuleName()));
    }

    public Integer getBestQuizScore(UserProfile profile, String courseId, String moduleName) {
        if (profile.getPassedModuleQuizzes() == null) {
            return null;
        }
        return profile.getPassedModuleQuizzes().stream()
                .filter(p -> courseId.equals(p.getCourseId()) && moduleName.equals(p.getModuleName()))
                .map(UserProfile.PassedModuleQuiz::getBestScore)
                .findFirst()
                .orElse(null);
    }

    private void recordPassedQuiz(
            String profilingSessionId, String userId, String courseId, String moduleName, int score, int maxScore) {

        UserProfile profile = resolveProfile(profilingSessionId, userId);
        if (profile == null) {
            return;
        }

        if (profile.getPassedModuleQuizzes() == null) {
            profile.setPassedModuleQuizzes(new ArrayList<>());
        }

        Optional<UserProfile.PassedModuleQuiz> existing = profile.getPassedModuleQuizzes().stream()
                .filter(p -> courseId.equals(p.getCourseId()) && moduleName.equals(p.getModuleName()))
                .findFirst();

        if (existing.isPresent()) {
            UserProfile.PassedModuleQuiz passed = existing.get();
            if (passed.getBestScore() == null || score > passed.getBestScore()) {
                passed.setBestScore(score);
                passed.setMaxScore(maxScore);
                passed.setPassedAt(IndiaTime.now());
            }
        } else {
            profile.getPassedModuleQuizzes().add(UserProfile.PassedModuleQuiz.builder()
                    .courseId(courseId)
                    .moduleName(moduleName)
                    .passedAt(IndiaTime.now())
                    .bestScore(score)
                    .maxScore(maxScore)
                    .build());
        }

        profile.setUpdatedAt(IndiaTime.now());
        userProfileRepository.save(profile);
    }

    private UserProfile resolveProfile(String profilingSessionId, String userId) {
        if (StringUtils.hasText(userId)) {
            return userProfileRepository.findByUserId(userId).orElse(null);
        }
        if (StringUtils.hasText(profilingSessionId)) {
            return userProfileRepository.findBySessionId(profilingSessionId).orElse(null);
        }
        return null;
    }

    private int countAttempts(String userId, String profilingSessionId, String courseId, String moduleName) {
        if (StringUtils.hasText(userId)) {
            return attemptRepository.countByUserIdAndCourseIdAndModuleName(userId, courseId, moduleName);
        }
        return attemptRepository.countByGuestSessionIdAndCourseIdAndModuleName(
                profilingSessionId, courseId, moduleName);
    }

    private void assertSessionOwnership(ModuleQuizSession session, String profilingSessionId, String userId) {
        if (StringUtils.hasText(userId)) {
            if (!userId.equals(session.getUserId())) {
                throw new IllegalArgumentException("Quiz session does not belong to this user");
            }
        } else if (!profilingSessionId.equals(session.getGuestSessionId())) {
            throw new IllegalArgumentException("Quiz session does not belong to this session");
        }
    }

    private void validateSessionRequest(CreateModuleQuizSessionRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (!StringUtils.hasText(request.getCourseId())) {
            throw new IllegalArgumentException("courseId is required");
        }
        if (!StringUtils.hasText(request.getModuleName())) {
            throw new IllegalArgumentException("moduleName is required");
        }
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("questions are required");
        }
        for (ModuleQuizQuestionDTO q : request.getQuestions()) {
            if (q.getId() == null || !StringUtils.hasText(q.getQuestion())) {
                throw new IllegalArgumentException("Each question must have id and question text");
            }
            if (q.getOptions() == null || q.getOptions().size() < 2) {
                throw new IllegalArgumentException("Each question must have at least 2 options");
            }
            if (!StringUtils.hasText(q.getCorrectKey())) {
                throw new IllegalArgumentException("Each question must have correctKey");
            }
        }
    }

    private ModuleQuizSession.QuizQuestion toSessionQuestion(ModuleQuizQuestionDTO dto) {
        List<ModuleQuizSession.QuizOption> options = dto.getOptions() == null
                ? new ArrayList<>()
                : dto.getOptions().stream()
                        .map(o -> ModuleQuizSession.QuizOption.builder()
                                .key(o.getKey())
                                .text(o.getText())
                                .build())
                        .collect(Collectors.toList());

        return ModuleQuizSession.QuizQuestion.builder()
                .id(dto.getId())
                .question(dto.getQuestion())
                .options(options)
                .correctKey(dto.getCorrectKey())
                .explanation(dto.getExplanation())
                .build();
    }

    private ModuleQuizQuestionDTO toClientQuestion(ModuleQuizSession.QuizQuestion q) {
        List<ModuleQuizOptionDTO> options = q.getOptions() == null
                ? new ArrayList<>()
                : q.getOptions().stream()
                        .map(o -> ModuleQuizOptionDTO.builder()
                                .key(o.getKey())
                                .text(o.getText())
                                .build())
                        .collect(Collectors.toList());

        return ModuleQuizQuestionDTO.builder()
                .id(q.getId())
                .question(q.getQuestion())
                .options(options)
                .build();
    }

    private ModuleQuizAttemptSummaryDTO toSummary(ModuleQuizAttempt attempt) {
        return ModuleQuizAttemptSummaryDTO.builder()
                .attemptId(attempt.getId())
                .courseId(attempt.getCourseId())
                .moduleName(attempt.getModuleName())
                .attemptNumber(attempt.getAttemptNumber())
                .score(attempt.getScore())
                .maxScore(attempt.getMaxScore())
                .percentage(attempt.getPercentage())
                .passed(attempt.getPassed())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .submittedAt(attempt.getSubmittedAt())
                .build();
    }

    private ModuleQuizAnswerResultDTO buildAnswerResult(
            ModuleQuizSession.QuizQuestion question, String selectedKey, boolean isCorrect) {
        List<ModuleQuizOptionDTO> optionDtos = toOptionDtos(question.getOptions());

        return ModuleQuizAnswerResultDTO.builder()
                .questionId(question.getId())
                .questionText(question.getQuestion())
                .selectedKey(selectedKey)
                .selectedOptionText(resolveOptionText(question.getOptions(), selectedKey))
                .correctKey(question.getCorrectKey())
                .correctOptionText(resolveOptionText(question.getOptions(), question.getCorrectKey()))
                .isCorrect(isCorrect)
                .explanation(question.getExplanation())
                .options(optionDtos)
                .build();
    }

    private List<ModuleQuizOptionDTO> toOptionDtos(List<ModuleQuizSession.QuizOption> options) {
        if (options == null) {
            return new ArrayList<>();
        }
        return options.stream()
                .map(o -> ModuleQuizOptionDTO.builder()
                        .key(o.getKey())
                        .text(o.getText())
                        .build())
                .collect(Collectors.toList());
    }

    private String resolveOptionText(List<ModuleQuizSession.QuizOption> options, String key) {
        if (!StringUtils.hasText(key) || options == null) {
            return null;
        }
        return options.stream()
                .filter(o -> key.equalsIgnoreCase(o.getKey()))
                .map(ModuleQuizSession.QuizOption::getText)
                .findFirst()
                .orElse(null);
    }
}
