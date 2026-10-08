package com.prwatech.skillama.service;

import com.prwatech.skillama.exception.InterviewFlowException;
import com.prwatech.skillama.model.AiMockInterviewConfig;
import com.prwatech.skillama.model.AiMockInterviewSession;
import com.prwatech.skillama.model.InterviewTurn;
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
    private java.time.Clock clock = java.time.Clock.systemUTC();

    @Autowired
    public AiMockInterviewService(
            AiMockInterviewConfigRepository configRepository,
            AiMockInterviewSessionRepository sessionRepository,
            SkillamaUserRepository userRepository,
            SkillamaAiClient skillamaAiClient) {
        this.configRepository = configRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.skillamaAiClient = skillamaAiClient;
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
        Instant started = Instant.now(clock);
        Instant ends = started.plus(Duration.ofMinutes(duration));
        String opening = resolveOpening(config);
        AiMockInterviewSession session = AiMockInterviewSession.builder()
                .userId(userId)
                .configId(config.getId())
                .title(config.getTitle())
                .style(config.getStyle())
                .durationMinutes(duration)
                .status("IN_PROGRESS")
                .startedAt(started)
                .endsAt(ends)
                .turns(new ArrayList<>(List.of(InterviewTurn.builder()
                        .role("AI")
                        .text(opening)
                        .at(started)
                        .build())))
                .build();
        session = sessionRepository.save(session);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", session.getId());
        response.put("id", session.getId());
        response.put("title", session.getTitle());
        response.put("status", session.getStatus());
        response.put("openingQuestion", opening);
        response.put("endsAtMs", ends.toEpochMilli());
        response.put("remainingSeconds", InterviewSessionRules.remainingSeconds(ends, started));
        return response;
    }

    public List<Map<String, Object>> listMine(String userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiMockInterviewSession session : sessionRepository.findByUserIdOrderByStartedAtDesc(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", session.getId());
            row.put("sessionId", session.getId());
            row.put("title", session.getTitle());
            row.put("configTitle", session.getTitle());
            row.put("status", session.getStatus());
            row.put("startedAt", session.getStartedAt());
            row.put("completedAt", session.getCompletedAt());
            row.put("durationSeconds", session.getDurationSeconds());
            row.put("feedbackSummary", session.getFeedbackSummary());
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> detail(String userId, String sessionId) {
        AiMockInterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> notFound("Practice session not found."));
        if (!userId.equals(session.getUserId())) {
            throw new InterviewFlowException("FORBIDDEN", HttpStatus.FORBIDDEN, "You cannot view this practice session.");
        }
        maybeFinish(session);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", session.getId());
        body.put("sessionId", session.getId());
        body.put("title", session.getTitle());
        body.put("status", session.getStatus());
        body.put("feedbackSummary", session.getFeedbackSummary());
        body.put("feedback", session.getFeedback());
        body.put("score", session.getScore());
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
        return body;
    }

    /**
     * The LMS practice room is a history view. When the wall-clock slot has ended,
     * score the transcript with the same evaluator as org interviews.
     */
    private void maybeFinish(AiMockInterviewSession session) {
        if ("COMPLETED".equals(session.getStatus()) || session.getEndsAt() == null) {
            return;
        }
        Instant now = Instant.now(clock);
        if (now.isBefore(session.getEndsAt())) {
            return;
        }
        session.setStatus("COMPLETED");
        session.setCompletedAt(session.getEndsAt());
        if (session.getStartedAt() != null) {
            session.setDurationSeconds((int) Math.max(0,
                    Duration.between(session.getStartedAt(), session.getEndsAt()).getSeconds()));
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
            session.setFeedbackSummary(summary == null ? null : summary.toString());
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
}
