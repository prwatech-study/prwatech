package com.prwatech.skillama.service;

import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.AiMockInterviewConfig;
import com.prwatech.skillama.model.AiMockInterviewSession;
import com.prwatech.skillama.model.InterviewTurn;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AiMockInterviewConfigRepository;
import com.prwatech.skillama.repository.AiMockInterviewSessionRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMockInterviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:20:00Z");

    @Mock private AiMockInterviewConfigRepository configRepository;
    @Mock private AiMockInterviewSessionRepository sessionRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private SkillamaAiClient skillamaAiClient;
    @Mock private AdminPermissionService adminPermissionService;

    private AiMockInterviewService service;

    @BeforeEach
    void setUp() {
        service = new AiMockInterviewService(
                configRepository,
                sessionRepository,
                userRepository,
                skillamaAiClient,
                adminPermissionService);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void joinArmsTheClockAndDoesNotBurnLobbyTime() {
        AiMockInterviewSession session = liveSession(null);
        session.setDurationMinutes(15);
        session.setStartedAt(NOW.minusSeconds(120));
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> joined = service.join("user-1", "s1");

        assertEquals(Boolean.TRUE, joined.get("clockArmed"));
        assertEquals(15 * 60L, ((Number) joined.get("remainingSeconds")).longValue());
        assertEquals(NOW, session.getStartedAt());
        assertEquals(NOW.plusSeconds(15 * 60L), session.getEndsAt());
    }

    @Test
    void joinIsIdempotentWhenClockAlreadyArmed() {
        Instant ends = NOW.plusSeconds(500);
        AiMockInterviewSession session = liveSession(ends);
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));

        Map<String, Object> joined = service.join("user-1", "s1");

        assertEquals(Boolean.TRUE, joined.get("clockArmed"));
        assertEquals(500L, ((Number) joined.get("remainingSeconds")).longValue());
        assertEquals(ends, session.getEndsAt());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void timeUpReturnsCloseAndDoesNotAskTheModel() {
        AiMockInterviewSession session = liveSession(NOW.minusSeconds(30));
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> next = service.next("user-1", "s1");

        assertEquals("CLOSE", next.get("action"));
        assertEquals(InterviewSessionRules.CLOSE_TEXT, next.get("text"));
        assertEquals(0, next.get("remainingSeconds"));
        verify(skillamaAiClient, never()).interviewNext(any());
    }

    @Test
    void timeRemainingAsksAFollowUpAndStoresTheTurn() {
        Instant ends = NOW.plusSeconds(90);
        AiMockInterviewSession session = liveSession(ends);
        session.setTurns(new ArrayList<>(List.of(
                InterviewTurn.builder().role("AI").text("Tell me about a project.").at(NOW).build(),
                InterviewTurn.builder().role("CANDIDATE").text("I owned the API.").at(NOW).build())));
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(configRepository.findById("cfg")).thenReturn(Optional.of(AiMockInterviewConfig.builder()
                .id("cfg")
                .style("TECHNICAL")
                .questions(List.of("How would you design a rate limiter?"))
                .build()));
        when(skillamaAiClient.interviewNext(any())).thenReturn(Map.of(
                "action", "FOLLOW_UP",
                "text", "What trade-off did you accept?"));

        Map<String, Object> next = service.next("user-1", "s1");

        assertEquals("FOLLOW_UP", next.get("action"));
        assertEquals("What trade-off did you accept?", next.get("text"));
        assertEquals("AI", session.getTurns().get(session.getTurns().size() - 1).getRole());
        assertEquals("What trade-off did you accept?", session.getTurns().get(session.getTurns().size() - 1).getText());
    }

    @Test
    void adminListSessionsIncludesCandidateEmail() {
        AiMockInterviewSession session = liveSession(NOW.plusSeconds(600));
        session.setTitle("Behavioral practice");
        session.setScore(78);
        when(sessionRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(session));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(
                User.builder().id("user-1").email("learner@example.com").build()));
        doNothing().when(adminPermissionService)
                .requirePermission(eq("admin-1"), eq(AdminModule.AI_INTERVIEWS), eq(AdminPermissionAction.READ));

        List<Map<String, Object>> rows = service.adminListSessions("admin-1", null);

        assertEquals(1, rows.size());
        assertEquals("learner@example.com", rows.get(0).get("candidateEmail"));
        assertEquals(78, rows.get(0).get("score"));
        assertEquals("Behavioral practice", rows.get(0).get("title"));
    }

    private static AiMockInterviewSession liveSession(Instant endsAt) {
        return AiMockInterviewSession.builder()
                .id("s1")
                .userId("user-1")
                .configId("cfg")
                .style("BEHAVIORAL")
                .status("IN_PROGRESS")
                .startedAt(NOW.minusSeconds(600))
                .endsAt(endsAt)
                .turns(new ArrayList<>())
                .build();
    }
}
