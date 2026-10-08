package com.prwatech.skillama.service;

import com.prwatech.skillama.exception.InterviewFlowException;
import com.prwatech.skillama.model.AiInterviewSchedule;
import com.prwatech.skillama.model.AiInterviewSession;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AiInterviewQuestionRepository;
import com.prwatech.skillama.repository.AiInterviewScheduleRepository;
import com.prwatech.skillama.repository.AiInterviewSessionRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInterviewServiceTest {

    private static final Instant START = Instant.parse("2026-10-08T10:00:00Z");

    @Mock private AiInterviewQuestionRepository questionRepository;
    @Mock private AiInterviewScheduleRepository scheduleRepository;
    @Mock private AiInterviewSessionRepository sessionRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private AdminPermissionService adminPermissionService;
    @Mock private SkillamaAiClient skillamaAiClient;
    @Mock private InterviewInviteMailer interviewInviteMailer;

    private AiInterviewService service;
    private AiInterviewSchedule schedule;
    private final AtomicReference<AiInterviewSession> sessions = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new AiInterviewService(
                questionRepository,
                scheduleRepository,
                sessionRepository,
                userRepository,
                organizationRepository,
                courseRepository,
                adminPermissionService,
                skillamaAiClient,
                interviewInviteMailer);
        schedule = AiInterviewSchedule.builder()
                .id("sch-1")
                .candidateEmail("a@b.com")
                .scheduledAt(START)
                .durationMinutes(20)
                .joinGraceMinutes(10)
                .style("MIXED")
                .inviteToken("tok")
                .status("SCHEDULED")
                .build();
        lenient().when(scheduleRepository.findByInviteToken("tok")).thenReturn(Optional.of(schedule));
        lenient().when(scheduleRepository.findById("sch-1")).thenReturn(Optional.of(schedule));
        lenient().when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(sessionRepository.findByScheduleId("sch-1")).thenAnswer(invocation -> Optional.ofNullable(sessions.get()));
        lenient().when(sessionRepository.findBySessionToken(any())).thenAnswer(invocation -> {
            AiInterviewSession stored = sessions.get();
            if (stored != null && invocation.getArgument(0).equals(stored.getSessionToken())) {
                return Optional.of(stored);
            }
            return Optional.empty();
        });
        lenient().when(sessionRepository.save(any(AiInterviewSession.class))).thenAnswer(invocation -> {
            AiInterviewSession stored = invocation.getArgument(0);
            if (stored.getId() == null) {
                stored.setId("sess-1");
            }
            sessions.set(stored);
            return stored;
        });
        lenient().when(skillamaAiClient.interviewStart(any())).thenReturn(Map.of("action", "ASK", "text", "Opening question"));
    }

    @Test
    void joinWindowRejectsTooEarlyAllowsEarlyArrivalAndGraceBoundary() {
        service.setClock(clock(START.minusSeconds(10 * 60 + 1)));
        InterviewFlowException early = assertThrows(InterviewFlowException.class, () -> join("client-a"));
        assertEquals("TOO_EARLY", early.getCode());

        service.setClock(clock(START.minusSeconds(5 * 60)));
        Map<String, Object> earlyJoin = join("client-early");
        assertEquals(START.plusSeconds(20 * 60).toEpochMilli(), ((Number) earlyJoin.get("endsAtMs")).longValue());

        sessions.set(null);
        schedule.setStatus("SCHEDULED");
        service.setClock(clock(START.plusSeconds(10 * 60)));
        Map<String, Object> onTheBoundary = join("client-a");
        assertEquals(START.plusSeconds(20 * 60).toEpochMilli(), ((Number) onTheBoundary.get("endsAtMs")).longValue());
        assertEquals(600L, ((Number) onTheBoundary.get("remainingSeconds")).longValue());

        sessions.set(null);
        schedule.setStatus("SCHEDULED");
        service.setClock(clock(START.plusSeconds(10 * 60 + 1)));
        InterviewFlowException closed = assertThrows(InterviewFlowException.class, () -> join("client-b"));
        assertEquals("JOIN_GRACE_EXPIRED", closed.getCode());
        verify(skillamaAiClient, never()).interviewNext(any());
    }

    @Test
    void parallelJoinIsRejectedUntilTheHeartbeatIsStale() {
        service.setClock(clock(START.plusSeconds(5 * 60)));
        Map<String, Object> first = join("client-a");

        service.setClock(clock(START.plusSeconds(6 * 60)));
        InterviewFlowException locked = assertThrows(InterviewFlowException.class, () -> join("client-b"));
        assertEquals("SESSION_LOCKED_ELSEWHERE", locked.getCode());
        assertEquals(first.get("sessionId"), sessions.get().getId());

        service.setClock(clock(START.plusSeconds(5 * 60 + 121)));
        Map<String, Object> reclaimed = join("client-b");
        assertNotEquals(first.get("sessionToken"), reclaimed.get("sessionToken"));
        assertEquals("sess-1", reclaimed.get("sessionId"));
        long expected = InterviewSessionRules.remainingSeconds(
                START.plusSeconds(20 * 60), START.plusSeconds(5 * 60 + 121));
        assertEquals(expected, ((Number) reclaimed.get("remainingSeconds")).longValue());
    }

    @Test
    void duplicateCreateLosesToTheLiveLock() {
        AiInterviewSession winner = AiInterviewSession.builder()
                .id("sess-winner")
                .scheduleId("sch-1")
                .sessionToken("owned-token")
                .clientInstanceId("client-a")
                .lastHeartbeatAt(START.plusSeconds(5 * 60))
                .status("IN_PROGRESS")
                .startedAt(START.plusSeconds(5 * 60))
                .endsAt(START.plusSeconds(20 * 60))
                .build();
        AtomicInteger lookups = new AtomicInteger();
        when(sessionRepository.findByScheduleId("sch-1")).thenAnswer(invocation -> {
            if (lookups.incrementAndGet() == 1) {
                return Optional.empty();
            }
            return Optional.of(winner);
        });
        when(sessionRepository.save(any(AiInterviewSession.class))).thenThrow(new DuplicateKeyException("scheduleId"));
        service.setClock(clock(START.plusSeconds(6 * 60)));

        InterviewFlowException locked = assertThrows(InterviewFlowException.class, () -> join("client-b"));
        assertEquals("SESSION_LOCKED_ELSEWHERE", locked.getCode());
    }

    @Test
    void resumeUsesWallClockRemainingAndDoesNotRestartTheSlot() {
        service.setClock(clock(START.plusSeconds(5 * 60)));
        Map<String, Object> joined = join("client-a");
        assertEquals(START.plusSeconds(20 * 60).toEpochMilli(), ((Number) joined.get("endsAtMs")).longValue());

        service.setClock(clock(START.plusSeconds(12 * 60)));
        Map<String, Object> resumed = join("client-a");
        assertEquals(joined.get("sessionToken"), resumed.get("sessionToken"));
        assertEquals(480L, ((Number) resumed.get("remainingSeconds")).longValue());
        assertEquals(joined.get("endsAtMs"), resumed.get("endsAtMs"));
    }

    @Test
    void timeUpClosesWithoutAskingTheModel() {
        service.setClock(clock(START.plusSeconds(5 * 60)));
        Map<String, Object> joined = join("client-a");
        service.setClock(clock(START.plusSeconds(20 * 60)));

        Map<String, Object> next = service.next((String) joined.get("sessionToken"));
        assertEquals("CLOSE", next.get("action"));
        assertEquals("CLOSING", next.get("phase"));
        assertEquals(InterviewSessionRules.CLOSE_TEXT, next.get("text"));
        verify(skillamaAiClient, never()).interviewNext(any());
    }

    @Test
    void timeRemainingProxiesTheServerClockNotAClientGuess() {
        when(skillamaAiClient.interviewNext(any())).thenReturn(Map.of("action", "ASK", "text", "Follow up?"));
        service.setClock(clock(START.plusSeconds(5 * 60)));
        Map<String, Object> joined = join("client-a");
        service.setClock(clock(START.plusSeconds(19 * 60)));

        Map<String, Object> next = service.next((String) joined.get("sessionToken"));
        assertEquals("ASK", next.get("action"));
        assertEquals(60L, ((Number) next.get("remainingSeconds")).longValue());
        verify(skillamaAiClient).interviewNext(org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(body ->
                ((Number) body.get("remainingSeconds")).longValue() == 60L));
    }

    @Test
    void candidateApisOmitScoreTranscriptAndStills() {
        when(skillamaAiClient.interviewEvaluate(any())).thenReturn(Map.of(
                "score", 88,
                "feedback", "Secret feedback",
                "feedbackSummary", "Hidden"));
        User candidate = new User();
        candidate.setId("learner-1");
        candidate.setEmail("A@B.com");
        when(userRepository.findById("learner-1")).thenReturn(Optional.of(candidate));
        when(scheduleRepository.findByCandidateEmailOrderByScheduledAtDesc("a@b.com")).thenReturn(List.of(schedule));

        service.setClock(clock(START.plusSeconds(5 * 60)));
        Map<String, Object> joined = join("client-a");
        Map<String, Object> ended = service.end((String) joined.get("sessionToken"));
        assertEquals("COMPLETED", ended.get("status"));
        assertFalse(ended.containsKey("score"));
        assertFalse(ended.containsKey("feedback"));
        assertFalse(ended.containsKey("turns"));
        assertEquals(88, sessions.get().getScore());

        Map<String, Object> mine = service.listMine("learner-1").get(0);
        assertEquals("COMPLETED", mine.get("status"));
        assertFalse(mine.containsKey("score"));
        assertFalse(mine.containsKey("feedback"));
        assertFalse(mine.containsKey("turns"));
        assertFalse(mine.containsKey("transcript"));
        assertFalse(mine.containsKey("snapshots"));
        assertFalse(mine.values().toString().contains("Secret"));

        Map<String, Object> admin = service.adminDetail("owner-1", "sch-1");
        assertEquals(88, admin.get("score"));
        assertEquals("Secret feedback", admin.get("feedback"));
        assertFalse(((List<?>) admin.get("turns")).isEmpty());

        InterviewFlowException retry = assertThrows(InterviewFlowException.class, () -> join("client-a"));
        assertEquals("ALREADY_COMPLETED", retry.getCode());
    }

    @Test
    void emailMustMatchTheInvite() {
        service.setClock(clock(START.plusSeconds(60)));
        InterviewFlowException mismatch = assertThrows(
                InterviewFlowException.class,
                () -> service.join("tok", Map.of("email", "other@b.com", "clientInstanceId", "client-a")));
        assertEquals("EMAIL_MISMATCH", mismatch.getCode());
        verify(skillamaAiClient, never()).interviewStart(any());
    }

    private Map<String, Object> join(String clientInstanceId) {
        return service.join("tok", Map.of("email", "a@b.com", "clientInstanceId", clientInstanceId));
    }

    private static Clock clock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
