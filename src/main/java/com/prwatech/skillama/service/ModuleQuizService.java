package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.*;
import com.prwatech.skillama.exception.AiBudgetLimitException;
import com.prwatech.skillama.exception.QuizGenerationFailedException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.ModuleQuizAttempt;
import com.prwatech.skillama.model.ModuleQuizSession;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.ModuleQuizAttemptRepository;
import com.prwatech.skillama.repository.ModuleQuizSessionRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleQuizService {

    public static final int PASSING_PERCENTAGE = 70;
    /** After this many failed attempts the learner may skip ahead with quiz still pending. */
    public static final int MIN_ATTEMPTS_BEFORE_SKIP = 2;
    private static final int SESSION_EXPIRY_HOURS = 2;
    private static final int NUM_QUESTIONS = 5;
    /** Server-enforced exam clock for a 5-question module quiz. */
    private static final int TIME_LIMIT_SECONDS = 600;

    private final ModuleQuizSessionRepository sessionRepository;
    private final ModuleQuizAttemptRepository attemptRepository;
    private final UserProfileRepository userProfileRepository;
    private final CourseRepository courseRepository;
    private final CourseCurriculumRepository curriculumRepository;
    private final SkillamaAiClient skillamaAiClient;
    private final SkillamaUserRepository userRepository;
    private final UserCourseService userCourseService;

    /**
     * Generates questions server-side (the client never supplies questions or an answer
     * key — see {@link SkillamaAiClient#generateQuizQuestions}) and opens a session with
     * a server-owned clock (startedAt/timeLimitSeconds), so neither the questions'
     * answer key nor the exam timer can be tampered with from the browser.
     */
    public CreateModuleQuizSessionResponseDTO createSession(
            String profilingSessionId, String userId, CreateModuleQuizSessionRequestDTO request) {

        validateSessionRequest(request);
        assertQuizEligible(profilingSessionId, userId, request.getCourseId(), request.getModuleName());

        User user = StringUtils.hasText(userId) ? userRepository.findById(userId).orElse(null) : null;

        String courseName = courseRepository.findById(request.getCourseId())
                .map(Course::getName)
                .orElse("this course");

        GeneratedQuizDTO generated;
        try {
            // user is nullable here (guests) — SkillamaAiClient's metered wrapper skips the
            // budget-check/recordUsage pair entirely when null.
            generated = skillamaAiClient.generateQuizQuestions(
                    user, "generate_module_quiz", request.getCourseId(),
                    courseName, request.getModuleName(), request.getTopics(), NUM_QUESTIONS, null);
            if (generated.getQuestions() == null || generated.getQuestions().isEmpty()) {
                throw new IllegalStateException("AI did not return any quiz questions");
            }
        } catch (AiBudgetLimitException e) {
            throw e;
        } catch (IllegalStateException e) {
            // Generation itself failed — no session/attempt exists to drive the normal
            // "skip after N failed attempts" flow, so count this toward that same threshold
            // instead, or a flaky AI response would strand the learner on this module forever.
            boolean skipEligible = recordGenerationFailure(
                    profilingSessionId, userId, request.getCourseId(), request.getModuleName());
            throw new QuizGenerationFailedException(e.getMessage(), skipEligible);
        }
        clearGenerationFailures(profilingSessionId, userId, request.getCourseId(), request.getModuleName());

        String quizSessionId = "quiz-" + UUID.randomUUID();
        LocalDateTime now = IndiaTime.now();

        List<ModuleQuizSession.QuizQuestion> questions = generated.getQuestions().stream()
                .map(this::toSessionQuestion)
                .collect(Collectors.toList());

        ModuleQuizSession session = ModuleQuizSession.builder()
                .quizSessionId(quizSessionId)
                .userId(userId)
                .guestSessionId(userId == null ? profilingSessionId : null)
                .courseId(request.getCourseId())
                .moduleName(request.getModuleName())
                .quizTitle(StringUtils.hasText(generated.getQuizTitle())
                        ? generated.getQuizTitle()
                        : "Module Quiz: " + request.getModuleName())
                .questions(questions)
                .createdAt(now)
                .startedAt(now)
                .timeLimitSeconds(TIME_LIMIT_SECONDS)
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
                .timeLimitSeconds(TIME_LIMIT_SECONDS)
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
                    .explanation(question.getExplanation())
                    .options(question.getOptions())
                    .build());

            answerResults.add(buildAnswerResult(question, selectedKey, isCorrect));
        }

        double percentage = maxScore > 0 ? (score * 100.0) / maxScore : 0.0;
        boolean passed = percentage >= PASSING_PERCENTAGE;

        LocalDateTime submittedAt = IndiaTime.now();
        Integer timeSpentSeconds = session.getStartedAt() != null
                ? (int) Duration.between(session.getStartedAt(), submittedAt).getSeconds()
                : null;
        Boolean overTimeLimit = session.getTimeLimitSeconds() != null && timeSpentSeconds != null
                && timeSpentSeconds > session.getTimeLimitSeconds();

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
                .timeSpentSeconds(timeSpentSeconds)
                .overTimeLimit(overTimeLimit)
                .submittedAt(submittedAt)
                .build();

        attempt = attemptRepository.save(attempt);

        if (passed) {
            recordPassedQuiz(profilingSessionId, userId, session.getCourseId(), session.getModuleName(), score, maxScore);
            refreshAggregateQuietly(userId, session.getCourseId());
        }

        return ModuleQuizAttemptResultDTO.builder()
                .attemptId(attempt.getId())
                .attemptNumber(attemptNumber)
                .score(score)
                .maxScore(maxScore)
                .percentage(percentage)
                .passed(passed)
                .passingPercentage(PASSING_PERCENTAGE)
                .timeSpentSeconds(timeSpentSeconds)
                .overTimeLimit(overTimeLimit)
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

    public List<ModuleQuizAttemptDetailDTO> getAttemptDetailsForUser(String userId, String courseId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        List<ModuleQuizAttempt> attempts = attemptRepository.findByUserIdOrderBySubmittedAtDesc(userId);
        return attempts.stream()
                .filter(a -> !StringUtils.hasText(courseId) || courseId.equals(a.getCourseId()))
                .map(this::toDetailWithCourseName)
                .collect(Collectors.toList());
    }

    /**
     * Full attempt history for the authenticated learner (or guest session), including answers.
     * courseId / moduleName are optional filters.
     */
    public List<ModuleQuizAttemptDetailDTO> getMyAttemptDetails(
            String profilingSessionId, String userId, String courseId, String moduleName) {
        List<ModuleQuizAttempt> attempts;
        if (StringUtils.hasText(userId)) {
            attempts = attemptRepository.findByUserIdOrderBySubmittedAtDesc(userId);
        } else if (StringUtils.hasText(profilingSessionId)) {
            attempts = attemptRepository.findByGuestSessionIdOrderBySubmittedAtDesc(profilingSessionId);
        } else {
            return List.of();
        }

        return attempts.stream()
                .filter(a -> !StringUtils.hasText(courseId) || courseId.equals(a.getCourseId()))
                .filter(a -> !StringUtils.hasText(moduleName) || moduleName.equals(a.getModuleName()))
                .map(this::toDetailWithCourseName)
                .collect(Collectors.toList());
    }

    private ModuleQuizAttemptDetailDTO toDetailWithCourseName(ModuleQuizAttempt attempt) {
        ModuleQuizAttemptDetailDTO detail = toDetail(attempt);
        String courseName = courseRepository.findById(attempt.getCourseId())
                .map(Course::getName)
                .orElse(attempt.getCourseId() != null ? attempt.getCourseId() : "Unknown course");
        detail.setCourseName(courseName);
        return detail;
    }

    /** Admin monitor: paginated Module Quiz attempts across all learners (guests excluded). */
    public Page<AdminModuleQuizAttemptDTO> listAdminAttempts(
            int page, int size, String userId, String courseId, String moduleName, String email) {
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

        List<AdminModuleQuizAttemptDTO> rows = attemptRepository.findAll().stream()
                .filter(a -> a.getUserId() != null) // guests have no stable identity to review
                .filter(a -> userId == null || userId.isBlank() || userId.equals(a.getUserId()))
                .filter(a -> courseId == null || courseId.isBlank() || courseId.equals(a.getCourseId()))
                .filter(a -> moduleName == null || moduleName.isBlank() || moduleName.equals(a.getModuleName()))
                .filter(a -> allowedUserIdsFinal == null || allowedUserIdsFinal.contains(a.getUserId()))
                .map(a -> {
                    User user = userCache.computeIfAbsent(a.getUserId(), id -> userRepository.findById(id).orElse(null));
                    String courseName = a.getCourseId() != null
                            ? courseNameCache.computeIfAbsent(a.getCourseId(),
                                    cid -> courseRepository.findById(cid).map(Course::getName).orElse(null))
                            : null;
                    return AdminModuleQuizAttemptDTO.builder()
                            .attemptId(a.getId())
                            .userId(a.getUserId())
                            .userName(user != null ? user.getName() : null)
                            .userEmail(user != null ? user.getEmail() : null)
                            .courseId(a.getCourseId())
                            .courseName(courseName)
                            .moduleName(a.getModuleName())
                            .attemptNumber(a.getAttemptNumber())
                            .score(a.getScore())
                            .maxScore(a.getMaxScore())
                            .percentage(a.getPercentage())
                            .passed(a.getPassed())
                            .timeSpentSeconds(a.getTimeSpentSeconds())
                            .overTimeLimit(a.getOverTimeLimit())
                            .submittedAt(a.getSubmittedAt())
                            .build();
                })
                .collect(Collectors.toList());

        rows.sort(Comparator.comparing(
                AdminModuleQuizAttemptDTO::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int total = rows.size();
        int from = pageNum * limit;
        if (from >= total) {
            return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), total);
        }
        int to = Math.min(from + limit, total);
        return new PageImpl<>(rows.subList(from, to), PageRequest.of(pageNum, limit), total);
    }

    public boolean hasPassedModuleQuiz(UserProfile profile, String courseId, String moduleName) {
        if (profile.getPassedModuleQuizzes() == null) {
            return false;
        }
        return profile.getPassedModuleQuizzes().stream()
                .anyMatch(p -> courseId.equals(p.getCourseId()) && moduleName.equals(p.getModuleName()));
    }

    public boolean hasSkippedModuleQuiz(UserProfile profile, String courseId, String moduleName) {
        if (profile == null || profile.getSkippedModuleQuizzes() == null) {
            return false;
        }
        return profile.getSkippedModuleQuizzes().stream()
                .anyMatch(s -> courseId.equals(s.getCourseId()) && moduleName.equals(s.getModuleName()));
    }

    public int countGenerationFailures(UserProfile profile, String courseId, String moduleName) {
        if (profile == null || profile.getQuizGenerationFailures() == null) {
            return 0;
        }
        return profile.getQuizGenerationFailures().stream()
                .filter(f -> courseId.equals(f.getCourseId()) && moduleName.equals(f.getModuleName()))
                .map(f -> f.getFailureCount() == null ? 0 : f.getFailureCount())
                .findFirst()
                .orElse(0);
    }

    /** Records a failed generation attempt and reports whether skip is now eligible. */
    private boolean recordGenerationFailure(
            String profilingSessionId, String userId, String courseId, String moduleName) {
        UserProfile profile = resolveProfile(profilingSessionId, userId);
        if (profile == null) {
            return false;
        }
        if (profile.getQuizGenerationFailures() == null) {
            profile.setQuizGenerationFailures(new ArrayList<>());
        }

        Optional<UserProfile.QuizGenerationFailure> existing = profile.getQuizGenerationFailures().stream()
                .filter(f -> courseId.equals(f.getCourseId()) && moduleName.equals(f.getModuleName()))
                .findFirst();

        int newCount;
        if (existing.isPresent()) {
            UserProfile.QuizGenerationFailure failure = existing.get();
            newCount = (failure.getFailureCount() == null ? 0 : failure.getFailureCount()) + 1;
            failure.setFailureCount(newCount);
            failure.setLastFailedAt(IndiaTime.now());
        } else {
            newCount = 1;
            profile.getQuizGenerationFailures().add(UserProfile.QuizGenerationFailure.builder()
                    .courseId(courseId)
                    .moduleName(moduleName)
                    .failureCount(newCount)
                    .lastFailedAt(IndiaTime.now())
                    .build());
        }

        profile.setUpdatedAt(IndiaTime.now());
        userProfileRepository.save(profile);

        int attemptCount = countAttempts(userId, profilingSessionId, courseId, moduleName);
        return (attemptCount + newCount) >= MIN_ATTEMPTS_BEFORE_SKIP;
    }

    /** Clears the failure streak for a module once its quiz generates successfully. */
    private void clearGenerationFailures(
            String profilingSessionId, String userId, String courseId, String moduleName) {
        UserProfile profile = resolveProfile(profilingSessionId, userId);
        if (profile == null || profile.getQuizGenerationFailures() == null) {
            return;
        }
        boolean removed = profile.getQuizGenerationFailures().removeIf(
                f -> courseId.equals(f.getCourseId()) && moduleName.equals(f.getModuleName()));
        if (removed) {
            profile.setUpdatedAt(IndiaTime.now());
            userProfileRepository.save(profile);
        }
    }

    /**
     * Unlock next module when the quiz is passed <em>or</em> explicitly skipped after retries.
     * Skipped quizzes remain "pending" for retake UI until they pass.
     */
    public boolean canUnlockPastModuleQuiz(UserProfile profile, String courseId, String moduleName) {
        return hasPassedModuleQuiz(profile, courseId, moduleName)
                || hasSkippedModuleQuiz(profile, courseId, moduleName);
    }

    public Map<String, Object> skipModuleQuiz(
            String profilingSessionId, String userId, String courseId, String moduleName) {
        if (!StringUtils.hasText(courseId) || !StringUtils.hasText(moduleName)) {
            throw new IllegalArgumentException("courseId and moduleName are required");
        }

        UserProfile profile = resolveProfile(profilingSessionId, userId);
        if (profile == null) {
            throw new IllegalArgumentException("User profile not found");
        }
        if (Boolean.TRUE.equals(profile.getIsGuest())) {
            throw new IllegalArgumentException("Guests cannot skip module quizzes");
        }
        if (hasPassedModuleQuiz(profile, courseId, moduleName)) {
            return Map.of("status", "ok", "skipped", false, "alreadyPassed", true);
        }
        if (hasSkippedModuleQuiz(profile, courseId, moduleName)) {
            return Map.of("status", "ok", "skipped", true, "alreadySkipped", true);
        }

        int attemptCount = countAttempts(userId, profilingSessionId, courseId, moduleName)
                + countGenerationFailures(profile, courseId, moduleName);
        if (attemptCount < MIN_ATTEMPTS_BEFORE_SKIP) {
            throw new IllegalArgumentException(
                    "Skip is available after " + MIN_ATTEMPTS_BEFORE_SKIP + " quiz attempts");
        }

        if (profile.getSkippedModuleQuizzes() == null) {
            profile.setSkippedModuleQuizzes(new ArrayList<>());
        }
        profile.getSkippedModuleQuizzes().add(UserProfile.SkippedModuleQuiz.builder()
                .courseId(courseId)
                .moduleName(moduleName)
                .skippedAt(IndiaTime.now())
                .attemptCountAtSkip(attemptCount)
                .build());
        profile.setUpdatedAt(IndiaTime.now());
        userProfileRepository.save(profile);

        refreshAggregateQuietly(
                StringUtils.hasText(userId) ? userId : profile.getUserId(), courseId);

        return Map.of(
                "status", "ok",
                "skipped", true,
                "attemptCount", attemptCount,
                "quizPending", true);
    }

    /**
     * The stored dashboard aggregate counts quizzes too (unified progress formula),
     * so a pass/skip must recompute it — best-effort, never failing the submit.
     */
    private void refreshAggregateQuietly(String userId, String courseId) {
        if (!StringUtils.hasText(userId)) {
            return; // guests have no dashboard aggregate
        }
        try {
            userCourseService.refreshCourseProgressAggregate(userId, courseId);
        } catch (Exception e) {
            log.warn("Failed to refresh progress aggregate for user {} course {}: {}",
                    userId, courseId, e.getMessage());
        }
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

    private void assertQuizEligible(
            String profilingSessionId, String userId, String courseId, String moduleName) {
        UserProfile profile = resolveProfile(profilingSessionId, userId);
        if (profile == null) {
            throw new IllegalArgumentException("User profile not found");
        }
        if (Boolean.TRUE.equals(profile.getIsGuest())) {
            return;
        }
        if (hasPassedModuleQuiz(profile, courseId, moduleName)) {
            throw new IllegalArgumentException("Module quiz already passed");
        }
        CourseCurriculum module = findModuleInCurriculum(courseId, moduleName);
        if (module == null) {
            throw new IllegalArgumentException("Module not found in course curriculum");
        }
        if (!isModuleLecturesCompleted(profile, module, courseId)) {
            throw new IllegalArgumentException(
                    "Complete all module lectures before starting the quiz");
        }
    }

    private CourseCurriculum findModuleInCurriculum(String courseId, String moduleName) {
        List<CourseCurriculum> curriculum =
                curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
        for (CourseCurriculum mod : curriculum) {
            if (moduleName.equals(mod.getModuleName())) {
                return mod;
            }
        }
        return null;
    }

    private boolean isModuleLecturesCompleted(
            UserProfile profile, CourseCurriculum module, String courseId) {
        if (module.getSubmodules() == null || module.getSubmodules().isEmpty()) {
            return false;
        }
        for (CourseCurriculum.Submodule submodule : module.getSubmodules()) {
            if (submodule.getEnabled() != null && !submodule.getEnabled()) {
                continue;
            }
            if (!isLectureCompleted(profile, submodule.getLabel(), courseId)) {
                return false;
            }
        }
        return true;
    }

    private boolean isLectureCompleted(UserProfile profile, String lectureLabel, String courseId) {
        if (profile.getCompletedLectures() == null) {
            return false;
        }
        return profile.getCompletedLectures().stream()
                .anyMatch(cl -> lectureLabel.equals(cl.getLectureLabel())
                        && courseId.equals(cl.getCourseId()));
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

    private ModuleQuizAttemptDetailDTO toDetail(ModuleQuizAttempt attempt) {
        List<ModuleQuizAnswerResultDTO> answers = attempt.getAnswers() == null
                ? new ArrayList<>()
                : attempt.getAnswers().stream()
                        .map(this::fromAnswerRecord)
                        .collect(Collectors.toList());

        return ModuleQuizAttemptDetailDTO.builder()
                .attemptId(attempt.getId())
                .courseId(attempt.getCourseId())
                .moduleName(attempt.getModuleName())
                .attemptNumber(attempt.getAttemptNumber())
                .score(attempt.getScore())
                .maxScore(attempt.getMaxScore())
                .percentage(attempt.getPercentage())
                .passed(attempt.getPassed())
                .passingPercentage(PASSING_PERCENTAGE)
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .submittedAt(attempt.getSubmittedAt())
                .answers(answers)
                .build();
    }

    private ModuleQuizAnswerResultDTO fromAnswerRecord(ModuleQuizAttempt.AnswerRecord record) {
        List<ModuleQuizOptionDTO> optionDtos = record.getOptions() == null
                ? new ArrayList<>()
                : record.getOptions().stream()
                        .map(o -> ModuleQuizOptionDTO.builder()
                                .key(o.getKey())
                                .text(o.getText())
                                .build())
                        .collect(Collectors.toList());

        return ModuleQuizAnswerResultDTO.builder()
                .questionId(record.getQuestionId())
                .questionText(record.getQuestionText())
                .selectedKey(record.getSelectedKey())
                .selectedOptionText(resolveOptionText(record.getOptions(), record.getSelectedKey()))
                .correctKey(record.getCorrectKey())
                .correctOptionText(resolveOptionText(record.getOptions(), record.getCorrectKey()))
                .isCorrect(record.getIsCorrect())
                .explanation(record.getExplanation())
                .options(optionDtos)
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
