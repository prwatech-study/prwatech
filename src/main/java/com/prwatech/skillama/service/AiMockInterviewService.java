package com.prwatech.skillama.service;

import com.prwatech.skillama.exception.InterviewFlowException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.AiMockInterviewConfig;
import com.prwatech.skillama.model.AiMockInterviewSession;
import com.prwatech.skillama.model.InterviewTurn;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AiMockInterviewConfigRepository;
import com.prwatech.skillama.repository.AiMockInterviewSessionRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Candidate practice interviews. Configs are platform-owned.
 * Unlike org interviews, the candidate can read their own feedback.
 */
@Service
@Slf4j
public class AiMockInterviewService {

    private final AiMockInterviewConfigRepository configRepository;
    private final AiMockInterviewSessionRepository sessionRepository;
    private final SkillamaUserRepository userRepository;
    private final SkillamaAiClient skillamaAiClient;
    private final AdminPermissionService adminPermissionService;
    private java.time.Clock clock = java.time.Clock.systemUTC();

    @Autowired
    public AiMockInterviewService(
            AiMockInterviewConfigRepository configRepository,
            AiMockInterviewSessionRepository sessionRepository,
            SkillamaUserRepository userRepository,
            SkillamaAiClient skillamaAiClient,
            AdminPermissionService adminPermissionService) {
        this.configRepository = configRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.skillamaAiClient = skillamaAiClient;
        this.adminPermissionService = adminPermissionService;
    }

    void setClock(java.time.Clock clock) {
        this.clock = clock;
    }

    public List<Map<String, Object>> listConfigs() {
        ensureSeeded();
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiMockInterviewConfig config : configRepository.findByActiveTrueOrderBySortOrderAsc()) {
            out.add(configView(config));
        }
        return out;
    }

    public Map<String, Object> start(String userId, Map<String, Object> body) {
        userRepository.findById(userId).orElseThrow(() -> notFound("User not found."));
        ensureSeeded();
        Object rawId = body == null ? null : body.get("configId");
        if (rawId == null || rawId.toString().isBlank()) {
            throw bad("configId is required.");
        }
        AiMockInterviewConfig config = configRepository.findById(rawId.toString())
                .orElseThrow(() -> notFound("Practice set not found."));
        if (!config.isActive()) {
            throw bad("This practice set is not available.");
        }
        int duration = config.getDurationMinutes() == null
                ? InterviewSessionRules.DEFAULT_DURATION_MINUTES : config.getDurationMinutes();
        Instant created = Instant.now(clock);
        String opening = resolveOpening(config);
        // Wall clock starts on join, not on lobby create — otherwise the countdown
        // burns while the candidate is still reading "Before you begin".
        AiMockInterviewSession session = AiMockInterviewSession.builder()
                .userId(userId)
                .configId(config.getId())
                .title(config.getTitle())
                .style(config.getStyle())
                .durationMinutes(duration)
                .status("IN_PROGRESS")
                .startedAt(created)
                .endsAt(null)
                .turns(new ArrayList<>(List.of(InterviewTurn.builder()
                        .role("AI")
                        .text(opening)
                        .at(created)
                        .build())))
                .build();
        session = sessionRepository.save(session);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", session.getId());
        response.put("id", session.getId());
        response.put("title", session.getTitle());
        response.put("status", session.getStatus());
        response.put("openingQuestion", opening);
        response.put("durationMinutes", duration);
        response.put("endsAtMs", null);
        response.put("remainingSeconds", duration * 60L);
        response.put("clockArmed", false);
        return response;
    }

    /**
     * Arms the practice wall clock when the candidate actually enters the room.
     * Idempotent if the clock was already started (resume).
     */
    public Map<String, Object> join(String userId, String sessionId) {
        AiMockInterviewSession session = requireOwned(userId, sessionId);
        if ("COMPLETED".equals(session.getStatus())) {
            throw alreadyCompleted();
        }
        Instant now = Instant.now(clock);
        boolean armed = ensureClockArmed(session, now);
        if (armed) {
            sessionRepository.save(session);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", session.getId());
        body.put("status", session.getStatus());
        body.put("durationMinutes", session.getDurationMinutes());
        body.put("endsAtMs", session.getEndsAt() == null ? null : session.getEndsAt().toEpochMilli());
        body.put("remainingSeconds", InterviewSessionRules.remainingSeconds(session.getEndsAt(), now));
        body.put("clockArmed", session.getEndsAt() != null);
        return body;
    }

    public List<Map<String, Object>> listMine(String userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiMockInterviewSession session : sessionRepository.findByUserIdOrderByStartedAtDesc(userId)) {
            out.add(listRow(session, null));
        }
        return out;
    }

    /** Admin monitor: all practice sessions with candidate email + score preview. */
    public List<Map<String, Object>> adminListSessions(String adminUserId, String status) {
        requireInterviewAdmin(adminUserId, AdminPermissionAction.READ);
        List<AiMockInterviewSession> sessions;
        if (status != null && !status.isBlank()) {
            sessions = sessionRepository.findByStatusOrderByStartedAtDesc(status.trim().toUpperCase(Locale.ROOT));
        } else {
            sessions = sessionRepository.findAllByOrderByStartedAtDesc();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiMockInterviewSession session : sessions) {
            out.add(listRow(session, resolveUserEmail(session.getUserId())));
        }
        return out;
    }

    /** Admin detail: full transcript, score, and feedback for any practice session. */
    public Map<String, Object> adminDetail(String adminUserId, String sessionId) {
        requireInterviewAdmin(adminUserId, AdminPermissionAction.READ);
        AiMockInterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> notFound("Practice session not found."));
        maybeFinish(session);
        Map<String, Object> body = sessionDetailBody(session);
        body.put("userId", session.getUserId());
        body.put("candidateEmail", resolveUserEmail(session.getUserId()));
        body.put("configId", session.getConfigId());
        body.put("style", session.getStyle());
        body.put("startedAt", session.getStartedAt());
        body.put("completedAt", session.getCompletedAt());
        body.put("durationSeconds", session.getDurationSeconds());
        return body;
    }

    public Map<String, Object> detail(String userId, String sessionId) {
        AiMockInterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> notFound("Practice session not found."));
        if (!userId.equals(session.getUserId())) {
            throw new InterviewFlowException("FORBIDDEN", HttpStatus.FORBIDDEN, "You cannot view this practice session.");
        }
        maybeFinish(session);
        return sessionDetailBody(session);
    }

    public Map<String, Object> addTurn(String userId, String sessionId, Map<String, Object> body) {
        AiMockInterviewSession session = requireOwned(userId, sessionId);
        if ("COMPLETED".equals(session.getStatus())) {
            throw alreadyCompleted();
        }
        String role = body == null ? null : stringVal(body.get("role"));
        if (role != null) {
            role = role.trim().toUpperCase(Locale.ROOT);
        }
        if (!"AI".equals(role) && !"CANDIDATE".equals(role)) {
            throw bad("Turn role must be AI or CANDIDATE.");
        }
        String text = body == null ? null : stringVal(body.get("text"));
        if (text == null || text.isBlank()) {
            throw bad("Turn text is required.");
        }
        appendTurn(session, role, text.trim());
        sessionRepository.save(session);
        return Map.of("ok", true, "turnCount", session.getTurns().size());
    }

    /**
     * Next interviewer line. The server clock decides. When time is up this returns CLOSE
     * and does not ask another question, even if the candidate just finished an answer.
     */
    public Map<String, Object> next(String userId, String sessionId) {
        AiMockInterviewSession session = requireOwned(userId, sessionId);
        if ("COMPLETED".equals(session.getStatus())) {
            return closeView();
        }
        Instant now = Instant.now(clock);
        if (ensureClockArmed(session, now)) {
            sessionRepository.save(session);
        }
        long remaining = InterviewSessionRules.remainingSeconds(session.getEndsAt(), now);
        if (InterviewSessionRules.timeUp(remaining)) {
            appendTurn(session, "AI", InterviewSessionRules.CLOSE_TEXT);
            sessionRepository.save(session);
            return closeView();
        }
        AiMockInterviewConfig config = configRepository.findById(session.getConfigId()).orElse(null);
        try {
            Map<String, Object> request = aiContext(session, config);
            request.put("remainingSeconds", remaining);
            Map<String, Object> response = skillamaAiClient.interviewNext(request);
            String action = stringVal(response.get("action"));
            if (action != null) {
                action = action.trim().toUpperCase(Locale.ROOT);
            }
            String text = stringVal(response.get("text"));
            if ("CLOSE".equals(action)) {
                if (text == null || text.isBlank()) {
                    text = InterviewSessionRules.CLOSE_TEXT;
                }
                appendTurn(session, "AI", text);
                sessionRepository.save(session);
                return closeView(text);
            }
            if (text == null || text.isBlank()) {
                text = fallbackFollowUp(session.getStyle());
                action = "ASK";
            }
            if (!"FOLLOW_UP".equals(action)) {
                action = "ASK";
            }
            appendTurn(session, "AI", text);
            sessionRepository.save(session);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", action);
            body.put("text", text);
            body.put("phase", "ACTIVE");
            body.put("remainingSeconds", remaining);
            return body;
        } catch (RuntimeException ex) {
            log.warn("Mock interview next fallback: {}", ex.getMessage());
            String text = fallbackFollowUp(session.getStyle());
            appendTurn(session, "AI", text);
            sessionRepository.save(session);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", "ASK");
            body.put("text", text);
            body.put("phase", "ACTIVE");
            body.put("remainingSeconds", remaining);
            return body;
        }
    }

    /** Ends the practice session and returns feedback the candidate is allowed to see. */
    public Map<String, Object> end(String userId, String sessionId) {
        AiMockInterviewSession session = requireOwned(userId, sessionId);
        if (!"COMPLETED".equals(session.getStatus())) {
            completeSession(session, Instant.now(clock));
        }
        return feedbackView(session);
    }

    /**
     * Abandoned sessions are scored when the wall-clock slot has ended.
     * A live room calls {@link #end} itself after the candidate finishes speaking.
     */
    private void maybeFinish(AiMockInterviewSession session) {
        if ("COMPLETED".equals(session.getStatus()) || session.getEndsAt() == null) {
            return;
        }
        Instant now = Instant.now(clock);
        if (now.isBefore(session.getEndsAt())) {
            return;
        }
        completeSession(session, session.getEndsAt());
    }

    private void completeSession(AiMockInterviewSession session, Instant completedAt) {
        Instant ended = completedAt == null ? Instant.now(clock) : completedAt;
        session.setStatus("COMPLETED");
        session.setCompletedAt(ended);
        if (session.getStartedAt() != null) {
            session.setDurationSeconds((int) Math.max(0,
                    Duration.between(session.getStartedAt(), ended).getSeconds()));
        }
        boolean answered = session.getTurns() != null && session.getTurns().stream()
                .anyMatch(turn -> "CANDIDATE".equals(turn.getRole()));
        if (!answered) {
            session.setFeedbackSummary("Practice session ended before an answer was recorded.");
            sessionRepository.save(session);
            return;
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("style", session.getStyle());
            request.put("course", "");
            request.put("jdText", "");
            request.put("transcript", transcript(session.getTurns()));
            Map<String, Object> evaluated = skillamaAiClient.interviewEvaluate(request);
            if (evaluated.get("score") instanceof Number number) {
                session.setScore(number.intValue());
            }
            Object feedback = evaluated.get("feedback");
            Object summary = evaluated.get("feedbackSummary");
            session.setFeedback(feedback == null ? null : feedback.toString());
            session.setFeedbackSummary(summary == null
                    ? (feedback == null ? "Practice session ended." : feedback.toString())
                    : summary.toString());
        } catch (RuntimeException ex) {
            log.warn("Mock interview evaluation fallback: {}", ex.getMessage());
            session.setFeedbackSummary("Practice session ended.");
        }
        sessionRepository.save(session);
    }

    private String resolveOpening(AiMockInterviewConfig config) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("style", config.getStyle());
            request.put("jdText", config.getJdText() == null ? "" : config.getJdText());
            request.put("course", config.getCourseName() == null ? "" : config.getCourseName());
            List<Map<String, String>> questions = new ArrayList<>();
            if (config.getQuestions() != null) {
                for (String text : config.getQuestions()) {
                    questions.add(Map.of("text", text));
                }
            }
            request.put("questions", questions);
            request.put("transcript", List.of());
            Map<String, Object> response = skillamaAiClient.interviewStart(request);
            Object text = response.get("text");
            if (text != null && !text.toString().isBlank()) {
                return text.toString();
            }
        } catch (RuntimeException ex) {
            log.warn("Mock interview opening fallback: {}", ex.getMessage());
        }
        if (config.getQuestions() != null) {
            for (String text : config.getQuestions()) {
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return "Tell me about a recent project and the part you owned.";
    }

    private void ensureSeeded() {
        if (configRepository.count() > 0) {
            return;
        }
        configRepository.saveAll(List.of(
                config("Behavioral practice", "Short behavioral practice. Not a hiring interview.",
                        "BEHAVIORAL", 15, 1, List.of(
                                "Tell me about a time you disagreed with a teammate.",
                                "Describe a time you missed a commitment and how you handled it.")),
                config("Technical oral", "Talk through a technical problem out loud.",
                        "TECHNICAL", 20, 2, List.of(
                                "How would you design a rate limiter for a public API?",
                                "What happens between a browser request and a database query?")),
                config("Mixed screen", "A short mix of behavioral and technical questions.",
                        "MIXED", 20, 3, List.of(
                                "What have you been working on recently?",
                                "Walk me through a bug you diagnosed and how you confirmed the fix."))));
    }

    private static AiMockInterviewConfig config(
            String title, String description, String style, int minutes, int sort, List<String> questions) {
        return AiMockInterviewConfig.builder()
                .title(title)
                .description(description)
                .style(style)
                .durationMinutes(minutes)
                .questions(questions)
                .active(true)
                .sortOrder(sort)
                .build();
    }

    private static Map<String, Object> configView(AiMockInterviewConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", config.getId());
        body.put("title", config.getTitle());
        body.put("name", config.getTitle());
        body.put("description", config.getDescription());
        body.put("style", config.getStyle());
        body.put("durationMinutes", config.getDurationMinutes());
        return body;
    }

    private static List<Map<String, Object>> transcript(List<InterviewTurn> turns) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (turns == null) {
            return out;
        }
        for (InterviewTurn turn : turns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", turn.getRole());
            row.put("text", turn.getText());
            out.add(row);
        }
        return out;
    }

    private static InterviewFlowException bad(String message) {
        return new InterviewFlowException("VALIDATION", HttpStatus.BAD_REQUEST, message);
    }

    private static InterviewFlowException notFound(String message) {
        return new InterviewFlowException("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }

    private static InterviewFlowException alreadyCompleted() {
        return new InterviewFlowException(
                "ALREADY_COMPLETED", HttpStatus.CONFLICT, "This practice session is already complete.");
    }

    private AiMockInterviewSession requireOwned(String userId, String sessionId) {
        AiMockInterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> notFound("Practice session not found."));
        if (!userId.equals(session.getUserId())) {
            throw new InterviewFlowException("FORBIDDEN", HttpStatus.FORBIDDEN, "You cannot view this practice session.");
        }
        return session;
    }

    private void requireInterviewAdmin(String userId, AdminPermissionAction action) {
        try {
            adminPermissionService.requirePermission(userId, AdminModule.AI_INTERVIEWS, action);
        } catch (ResourceNotFoundException ex) {
            throw notFound(ex.getMessage());
        } catch (RuntimeException ex) {
            throw new InterviewFlowException(
                    "FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    ex.getMessage() == null ? "You do not have permission to perform this action." : ex.getMessage());
        }
    }

    private Map<String, Object> sessionDetailBody(AiMockInterviewSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", session.getId());
        body.put("sessionId", session.getId());
        body.put("title", session.getTitle());
        body.put("status", session.getStatus());
        body.put("feedbackSummary", session.getFeedbackSummary());
        body.put("feedback", session.getFeedback());
        body.put("score", session.getScore());
        Instant now = Instant.now(clock);
        body.put("endsAtMs", session.getEndsAt() == null ? null : session.getEndsAt().toEpochMilli());
        body.put("clockArmed", session.getEndsAt() != null);
        body.put("durationMinutes", session.getDurationMinutes());
        if (session.getEndsAt() != null) {
            body.put("remainingSeconds", InterviewSessionRules.remainingSeconds(session.getEndsAt(), now));
        } else {
            int minutes = session.getDurationMinutes() == null
                    ? InterviewSessionRules.DEFAULT_DURATION_MINUTES : session.getDurationMinutes();
            body.put("remainingSeconds", minutes * 60L);
        }
        List<Map<String, Object>> turns = new ArrayList<>();
        if (session.getTurns() != null) {
            for (InterviewTurn turn : session.getTurns()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", turn.getRole());
                row.put("text", turn.getText());
                turns.add(row);
            }
        }
        body.put("turns", turns);
        body.put("transcript", turns);
        return body;
    }

    private Map<String, Object> listRow(AiMockInterviewSession session, String candidateEmail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", session.getId());
        row.put("sessionId", session.getId());
        row.put("title", session.getTitle());
        row.put("configTitle", session.getTitle());
        row.put("status", session.getStatus());
        row.put("startedAt", session.getStartedAt());
        row.put("completedAt", session.getCompletedAt());
        row.put("durationSeconds", session.getDurationSeconds());
        row.put("durationMinutes", session.getDurationMinutes());
        row.put("score", session.getScore());
        row.put("feedbackSummary", session.getFeedbackSummary());
        row.put("style", session.getStyle());
        if (candidateEmail != null) {
            row.put("candidateEmail", candidateEmail);
            row.put("userId", session.getUserId());
        }
        return row;
    }

    private String resolveUserEmail(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userRepository.findById(userId).map(User::getEmail).orElse(null);
    }

    /** @return true when the session was updated and needs saving */
    private boolean ensureClockArmed(AiMockInterviewSession session, Instant now) {
        if (session.getEndsAt() != null) {
            return false;
        }
        int duration = session.getDurationMinutes() == null
                ? InterviewSessionRules.DEFAULT_DURATION_MINUTES : session.getDurationMinutes();
        session.setStartedAt(now);
        session.setEndsAt(now.plus(Duration.ofMinutes(duration)));
        return true;
    }

    private Map<String, Object> aiContext(AiMockInterviewSession session, AiMockInterviewConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("style", session.getStyle());
        body.put("jdText", config == null || config.getJdText() == null ? "" : config.getJdText());
        body.put("course", config == null || config.getCourseName() == null ? "" : config.getCourseName());
        List<Map<String, String>> questions = new ArrayList<>();
        if (config != null && config.getQuestions() != null) {
            for (String text : config.getQuestions()) {
                if (text != null && !text.isBlank()) {
                    questions.add(Map.of("text", text));
                }
            }
        }
        body.put("questions", questions);
        body.put("transcript", transcript(session.getTurns()));
        return body;
    }

    private void appendTurn(AiMockInterviewSession session, String role, String text) {
        List<InterviewTurn> turns = session.getTurns() == null
                ? new ArrayList<>() : new ArrayList<>(session.getTurns());
        InterviewTurn last = turns.isEmpty() ? null : turns.get(turns.size() - 1);
        if (last != null && role.equals(last.getRole()) && text.equals(last.getText())) {
            return;
        }
        turns.add(InterviewTurn.builder().role(role).text(text).at(Instant.now(clock)).build());
        session.setTurns(turns);
    }

    private static String fallbackFollowUp(String style) {
        if ("TECHNICAL".equals(style)) {
            return "What trade-off did you consider, and why did you choose that approach?";
        }
        if ("BEHAVIORAL".equals(style)) {
            return "What was your specific contribution, and what would you do differently next time?";
        }
        return "Can you go one level deeper on what you just described?";
    }

    private static Map<String, Object> closeView() {
        return closeView(InterviewSessionRules.CLOSE_TEXT);
    }

    private static Map<String, Object> closeView(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", "CLOSE");
        body.put("text", text);
        body.put("phase", "CLOSING");
        body.put("remainingSeconds", 0);
        return body;
    }

    private static Map<String, Object> feedbackView(AiMockInterviewSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "COMPLETED");
        body.put("score", session.getScore());
        body.put("feedback", session.getFeedback());
        body.put("feedbackSummary", session.getFeedbackSummary());
        return body;
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
