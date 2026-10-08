package com.prwatech.skillama.service;

import com.prwatech.skillama.exception.InterviewFlowException;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.AdminModule;
import com.prwatech.skillama.model.AdminPermissionAction;
import com.prwatech.skillama.model.AiInterviewQuestion;
import com.prwatech.skillama.model.AiInterviewSchedule;
import com.prwatech.skillama.model.AiInterviewSession;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.InterviewRescheduleEvent;
import com.prwatech.skillama.model.InterviewSeedQuestion;
import com.prwatech.skillama.model.InterviewSnapshot;
import com.prwatech.skillama.model.InterviewTurn;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.AiInterviewQuestionRepository;
import com.prwatech.skillama.repository.AiInterviewScheduleRepository;
import com.prwatech.skillama.repository.AiInterviewSessionRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Org-scheduled AI Interview. The server owns the slot end and the single-session lock.
 * Candidate responses never include score, feedback, transcript, or stills.
 */
@Service
@Slf4j
public class AiInterviewService {

    static final Set<String> STYLES = Set.of("BEHAVIORAL", "TECHNICAL", "MIXED");
    static final Set<String> LIVE_SCHEDULE = Set.of("SCHEDULED", "JOINABLE", "IN_PROGRESS", "WINDING_DOWN");
    static final int MAX_SNAPSHOT_BYTES = 1_500_000;

    private final AiInterviewQuestionRepository questionRepository;
    private final AiInterviewScheduleRepository scheduleRepository;
    private final AiInterviewSessionRepository sessionRepository;
    private final SkillamaUserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final CourseRepository courseRepository;
    private final AdminPermissionService adminPermissionService;
    private final SkillamaAiClient skillamaAiClient;
    private final InterviewInviteMailer interviewInviteMailer;
    private final SecureRandom secureRandom = new SecureRandom();

    private java.time.Clock clock = java.time.Clock.systemUTC();

    @Autowired
    public AiInterviewService(
            AiInterviewQuestionRepository questionRepository,
            AiInterviewScheduleRepository scheduleRepository,
            AiInterviewSessionRepository sessionRepository,
            SkillamaUserRepository userRepository,
            OrganizationRepository organizationRepository,
            CourseRepository courseRepository,
            AdminPermissionService adminPermissionService,
            SkillamaAiClient skillamaAiClient,
            InterviewInviteMailer interviewInviteMailer) {
        this.questionRepository = questionRepository;
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.courseRepository = courseRepository;
        this.adminPermissionService = adminPermissionService;
        this.skillamaAiClient = skillamaAiClient;
        this.interviewInviteMailer = interviewInviteMailer;
    }

    /** Tests pin the wall clock. Production uses UTC. */
    void setClock(java.time.Clock clock) {
        this.clock = clock;
    }

    public List<Map<String, Object>> listQuestions(String userId, String tag, String query) {
        requireInterviewAdmin(userId, AdminPermissionAction.READ);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String tagFilter = tag == null || tag.isBlank() ? null : tag.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiInterviewQuestion question : questionRepository.findAllByOrderByCreatedAtDesc()) {
            if (tagFilter != null && !tagFilter.equals(question.getTag())) {
                continue;
            }
            if (!needle.isEmpty() && (question.getText() == null
                    || !question.getText().toLowerCase(Locale.ROOT).contains(needle))) {
                continue;
            }
            out.add(questionView(question));
        }
        return out;
    }

    public Map<String, Object> createQuestion(String userId, Map<String, Object> body) {
        requireInterviewAdmin(userId, AdminPermissionAction.CREATE);
        AiInterviewQuestion question = AiInterviewQuestion.builder()
                .organizationId(blankToNull(stringVal(body.get("organizationId"))))
                .text(requireText(body.get("text"), "Question text is required."))
                .tag(requireStyle(body.get("tag"), "Question tag"))
                .createdBy(userId)
                .createdAt(now())
                .updatedAt(now())
                .build();
        return questionView(questionRepository.save(question));
    }

    public Map<String, Object> updateQuestion(String userId, String questionId, Map<String, Object> body) {
        requireInterviewAdmin(userId, AdminPermissionAction.UPDATE);
        AiInterviewQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> notFound("Question not found."));
        if (body.get("text") != null) {
            question.setText(requireText(body.get("text"), "Question text is required."));
        }
        if (body.get("tag") != null) {
            question.setTag(requireStyle(body.get("tag"), "Question tag"));
        }
        question.setUpdatedAt(now());
        return questionView(questionRepository.save(question));
    }

    public Map<String, Object> deleteQuestion(String userId, String questionId) {
        requireInterviewAdmin(userId, AdminPermissionAction.DELETE);
        if (!questionRepository.existsById(questionId)) {
            throw notFound("Question not found.");
        }
        questionRepository.deleteById(questionId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", questionId);
        body.put("deleted", true);
        return body;
    }

    public List<Map<String, Object>> listSchedules(String userId, String status) {
        requireInterviewAdmin(userId, AdminPermissionAction.READ);
        Instant now = now();
        List<AiInterviewSchedule> rows = status == null || status.isBlank()
                ? scheduleRepository.findAllByOrderByScheduledAtDesc()
                : scheduleRepository.findByStatusOrderByScheduledAtDesc(status.trim().toUpperCase(Locale.ROOT));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiInterviewSchedule schedule : rows) {
            refreshScheduleStatus(schedule, now);
            if (status != null && !status.isBlank()
                    && !status.trim().equalsIgnoreCase(schedule.getStatus())) {
                continue;
            }
            out.add(scheduleView(schedule));
        }
        return out;
    }

    public Map<String, Object> createSchedule(String userId, Map<String, Object> body) {
        requireInterviewAdmin(userId, AdminPermissionAction.CREATE);
        User actor = userRepository.findById(userId).orElseThrow(() -> notFound("User not found."));
        String email = normalizeEmail(requireText(body.get("candidateEmail"), "Candidate email is required."));
        assertNoLiveSchedule(email, null);
        Instant scheduledAt = parseInstant(body.get("scheduledAt"), "scheduledAt is required.");
        int duration = positiveInt(body.get("durationMinutes"), InterviewSessionRules.DEFAULT_DURATION_MINUTES, 1, 180);
        int grace = body.get("joinGraceMinutes") == null
                ? InterviewSessionRules.DEFAULT_JOIN_GRACE_MINUTES
                : positiveInt(body.get("joinGraceMinutes"), InterviewSessionRules.DEFAULT_JOIN_GRACE_MINUTES, 0, 120);
        String style = requireStyle(body.get("style") == null ? "MIXED" : body.get("style"), "Style");
        String organizationId = resolveOrganizationId(body, actor, email);
        String courseId = blankToNull(stringVal(body.get("courseId")));
        String courseName = resolveCourseName(courseId);
        List<InterviewSeedQuestion> seeds = loadSeeds(stringList(body.get("questionIds")));

        AiInterviewSchedule schedule = AiInterviewSchedule.builder()
                .organizationId(organizationId)
                .candidateEmail(email)
                .scheduledAt(scheduledAt)
                .durationMinutes(duration)
                .joinGraceMinutes(grace)
                .style(style)
                .courseId(courseId)
                .courseName(courseName)
                .jdText(clip(stringVal(body.get("jdText")), 20000))
                .questionIds(seeds.stream().map(InterviewSeedQuestion::getId).toList())
                .seedQuestions(seeds)
                .inviteToken(newInviteToken())
                .status("SCHEDULED")
                .createdBy(userId)
                .createdAt(now())
                .rescheduleLog(new ArrayList<>())
                .build();
        schedule = scheduleRepository.save(schedule);
        schedule.setEmailSent(sendInvite(schedule));
        schedule = scheduleRepository.save(schedule);
        return scheduleView(schedule);
    }

    public Map<String, Object> reschedule(String userId, String scheduleId, Map<String, Object> body) {
        requireInterviewAdmin(userId, AdminPermissionAction.UPDATE);
        String reason = requireText(body.get("reason"), "A reason is required to reschedule.");
        Instant newStart = parseInstant(body.get("scheduledAt"), "scheduledAt is required.");
        AiInterviewSchedule previous = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> notFound("Schedule not found."));
        if ("RESCHEDULED".equals(previous.getStatus()) || "CANCELLED".equals(previous.getStatus())) {
            throw bad("NOT_RESCHEDULABLE", "This schedule was already replaced. Reschedule the new invite.");
        }
        sessionRepository.findByScheduleId(previous.getId()).ifPresent(session -> {
            if ("IN_PROGRESS".equals(session.getStatus()) || "WINDING_DOWN".equals(session.getStatus())) {
                throw bad("INTERVIEW_IN_PROGRESS", "This interview is live. Reschedule after it ends.");
            }
        });

        Instant oldStart = previous.getScheduledAt();
        previous.setStatus("RESCHEDULED");
        scheduleRepository.save(previous);
        assertNoLiveSchedule(previous.getCandidateEmail(), previous.getId());

        AiInterviewSchedule created = AiInterviewSchedule.builder()
                .organizationId(previous.getOrganizationId())
                .candidateEmail(previous.getCandidateEmail())
                .scheduledAt(newStart)
                .durationMinutes(previous.getDurationMinutes())
                .joinGraceMinutes(previous.getJoinGraceMinutes())
                .style(previous.getStyle())
                .courseId(previous.getCourseId())
                .courseName(previous.getCourseName())
                .jdText(previous.getJdText())
                .questionIds(copyStrings(previous.getQuestionIds()))
                .seedQuestions(copySeeds(previous.getSeedQuestions()))
                .inviteToken(newInviteToken())
                .status("SCHEDULED")
                .previousScheduleId(previous.getId())
                .createdBy(userId)
                .createdAt(now())
                .rescheduleLog(new ArrayList<>())
                .build();
        created = scheduleRepository.save(created);

        InterviewRescheduleEvent event = InterviewRescheduleEvent.builder()
                .from(oldStart)
                .to(newStart)
                .reason(reason)
                .at(now())
                .actorId(userId)
                .previousScheduleId(previous.getId())
                .newScheduleId(created.getId())
                .build();
        previous.setRescheduleLog(appendEvent(previous.getRescheduleLog(), event));
        created.setRescheduleLog(appendEvent(created.getRescheduleLog(), event));
        scheduleRepository.save(previous);
        created.setEmailSent(sendInvite(created));
        created = scheduleRepository.save(created);
        return scheduleView(created);
    }

    public Map<String, Object> adminDetail(String userId, String scheduleId) {
        requireInterviewAdmin(userId, AdminPermissionAction.READ);
        AiInterviewSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> notFound("Schedule not found."));
        AiInterviewSession session = sessionRepository.findByScheduleId(scheduleId).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", schedule.getId());
        body.put("candidateEmail", schedule.getCandidateEmail());
        body.put("status", schedule.getStatus());
        body.put("scheduledAt", schedule.getScheduledAt());
        body.put("durationMinutes", schedule.getDurationMinutes());
        body.put("style", schedule.getStyle());
        body.put("score", session == null ? null : session.getScore());
        body.put("feedback", session == null ? null : session.getFeedback());
        body.put("minutesSpent", session == null ? null : session.getMinutesSpent());
        body.put("creditEstimate", session == null ? null : session.getCreditEstimate());
        body.put("turns", session == null ? List.of() : turnViews(session.getTurns()));
        body.put("transcript", body.get("turns"));
        body.put("snapshots", session == null ? List.of() : snapshotViews(session.getSnapshots()));
        body.put("rescheduleLog", rescheduleViews(schedule.getRescheduleLog()));
        return body;
    }

    public Map<String, Object> usage(String userId, String from, String to) {
        requireInterviewAdmin(userId, AdminPermissionAction.READ);
        Instant fromInstant = parseOptionalInstant(from, false);
        Instant toInstant = parseOptionalInstant(to, true);
        List<AiInterviewSession> sessions = fromInstant != null && toInstant != null
                ? sessionRepository.findByStatusAndCompletedAtBetween("COMPLETED", fromInstant, toInstant)
                : sessionRepository.findByStatus("COMPLETED");
        double minutes = 0;
        double credits = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AiInterviewSession session : sessions) {
            if (fromInstant != null && toInstant == null && session.getCompletedAt() != null
                    && session.getCompletedAt().isBefore(fromInstant)) {
                continue;
            }
            if (toInstant != null && fromInstant == null && session.getCompletedAt() != null
                    && session.getCompletedAt().isAfter(toInstant)) {
                continue;
            }
            double rowMinutes = session.getMinutesSpent() == null ? 0 : session.getMinutesSpent();
            double rowCredits = session.getCreditEstimate() == null ? 0 : session.getCreditEstimate();
            minutes += rowMinutes;
            credits += rowCredits;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scheduleId", session.getScheduleId());
            row.put("candidateEmail", session.getCandidateEmail());
            row.put("organizationId", session.getOrganizationId());
            row.put("minutesSpent", rowMinutes);
            row.put("creditEstimate", rowCredits);
            row.put("completedAt", session.getCompletedAt());
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalMinutesSpent", round2(minutes));
        body.put("totalCreditEstimate", round2(credits));
        body.put("creditsPerMinute", InterviewSessionRules.CREDITS_PER_MINUTE);
        body.put("sessions", rows);
        return body;
    }

    /** Candidate list: upcoming and past only. No grades. */
    public List<Map<String, Object>> listMine(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> notFound("User not found."));
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return List.of();
        }
        Instant now = now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiInterviewSchedule schedule : scheduleRepository
                .findByCandidateEmailOrderByScheduledAtDesc(normalizeEmail(user.getEmail()))) {
            refreshScheduleStatus(schedule, now);
            out.add(candidateView(schedule));
        }
        return out;
    }

    public Map<String, Object> preview(String token) {
        AiInterviewSchedule schedule = scheduleRepository.findByInviteToken(token)
                .orElseThrow(() -> notFound("This interview invite was not found."));
        refreshScheduleStatus(schedule, now());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scheduledAt", schedule.getScheduledAt());
        body.put("durationMinutes", schedule.getDurationMinutes());
        body.put("joinGraceMinutes", schedule.getJoinGraceMinutes());
        body.put("style", schedule.getStyle());
        body.put("status", schedule.getStatus());
        body.put("orgName", organizationName(schedule.getOrganizationId()));
        return body;
    }

    public Map<String, Object> join(String token, Map<String, Object> body) {
        String email = normalizeEmail(requireText(body.get("email"), "Email is required."));
        String clientInstanceId = requireText(body.get("clientInstanceId"), "clientInstanceId is required.");
        AiInterviewSchedule schedule = scheduleRepository.findByInviteToken(token)
                .orElseThrow(() -> notFound("This interview invite was not found."));
        if (!email.equals(schedule.getCandidateEmail())) {
            throw new InterviewFlowException(
                    "EMAIL_MISMATCH",
                    HttpStatus.FORBIDDEN,
                    "This invite was sent to a different email address.");
        }
        if ("RESCHEDULED".equals(schedule.getStatus()) || "CANCELLED".equals(schedule.getStatus())) {
            throw bad("NOT_JOINABLE", "This interview invite is no longer active.");
        }
        Instant now = now();
        AiInterviewSession existing = sessionRepository.findByScheduleId(schedule.getId()).orElse(null);
        if (existing != null) {
            return resumeOrReject(schedule, existing, clientInstanceId, now);
        }
        if ("COMPLETED".equals(schedule.getStatus())) {
            throw alreadyCompleted();
        }
        Instant slotEnd = InterviewSessionRules.endsAt(schedule.getScheduledAt(), schedule.getDurationMinutes());
        String denial = InterviewSessionRules.firstJoinDenial(
                schedule.getScheduledAt(),
                schedule.getJoinGraceMinutes() == null
                        ? InterviewSessionRules.DEFAULT_JOIN_GRACE_MINUTES
                        : schedule.getJoinGraceMinutes(),
                slotEnd,
                now);
        if (denial != null) {
            if (!"TOO_EARLY".equals(denial) && !"INVALID_SCHEDULE".equals(denial)) {
                schedule.setStatus("EXPIRED");
                scheduleRepository.save(schedule);
            }
            throw windowError(denial);
        }

        AiInterviewSession created = AiInterviewSession.builder()
                .scheduleId(schedule.getId())
                .organizationId(schedule.getOrganizationId())
                .candidateEmail(schedule.getCandidateEmail())
                .sessionToken(newSessionToken())
                .clientInstanceId(clientInstanceId)
                .lastHeartbeatAt(now)
                .status("IN_PROGRESS")
                .startedAt(now)
                .endsAt(slotEnd)
                .turns(new ArrayList<>())
                .snapshots(new ArrayList<>())
                .build();
        try {
            created = sessionRepository.save(created);
        } catch (DuplicateKeyException ex) {
            AiInterviewSession raced = sessionRepository.findByScheduleId(schedule.getId())
                    .orElseThrow(() -> bad("SESSION_LOCKED_ELSEWHERE", lockMessage()));
            return resumeOrReject(schedule, raced, clientInstanceId, now);
        }
        String opening = resolveOpening(schedule);
        appendTurn(created, "AI", opening);
        sessionRepository.save(created);
        schedule.setStatus("IN_PROGRESS");
        scheduleRepository.save(schedule);
        return joinView(created, opening, now);
    }

    public Map<String, Object> heartbeat(String sessionToken) {
        AiInterviewSession session = requireSession(sessionToken);
        if ("COMPLETED".equals(session.getStatus())) {
            return Map.of("status", "COMPLETED", "remainingSeconds", 0);
        }
        session.setLastHeartbeatAt(now());
        sessionRepository.save(session);
        return clockView(session, now());
    }

    public Map<String, Object> addTurn(String sessionToken, Map<String, Object> body) {
        AiInterviewSession session = requireSession(sessionToken);
        if ("COMPLETED".equals(session.getStatus())) {
            throw alreadyCompleted();
        }
        String role = stringVal(body.get("role"));
        if (role != null) {
            role = role.trim().toUpperCase(Locale.ROOT);
        }
        if (!"AI".equals(role) && !"CANDIDATE".equals(role)) {
            throw bad("INVALID_ROLE", "Turn role must be AI or CANDIDATE.");
        }
        String text = requireText(body.get("text"), "Turn text is required.");
        appendTurn(session, role, text);
        session.setLastHeartbeatAt(now());
        sessionRepository.save(session);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("turnCount", session.getTurns().size());
        return response;
    }

    public Map<String, Object> next(String sessionToken) {
        AiInterviewSession session = requireSession(sessionToken);
        if ("COMPLETED".equals(session.getStatus())) {
            return closeView();
        }
        Instant now = now();
        session.setLastHeartbeatAt(now);
        AiInterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId())
                .orElseThrow(() -> notFound("Schedule not found."));
        long remaining = InterviewSessionRules.remainingSeconds(session.getEndsAt(), now);
        if (InterviewSessionRules.timeUp(remaining)) {
            markWindingDown(schedule, session);
            return closeView();
        }
        try {
            Map<String, Object> request = aiContext(schedule, session.getTurns());
            request.put("remainingSeconds", remaining);
            Map<String, Object> response = skillamaAiClient.interviewNext(request);
            String action = stringVal(response.get("action"));
            if (action != null) {
                action = action.trim().toUpperCase(Locale.ROOT);
            }
            if ("CLOSE".equals(action) || InterviewSessionRules.timeUp(remaining)) {
                markWindingDown(schedule, session);
                String text = stringVal(response.get("text"));
                return closeView(text == null || text.isBlank() ? InterviewSessionRules.CLOSE_TEXT : text);
            }
            sessionRepository.save(session);
            String text = stringVal(response.get("text"));
            if (text == null || text.isBlank()) {
                text = fallbackFollowUp(schedule.getStyle());
                action = "ASK";
            }
            if (!"FOLLOW_UP".equals(action)) {
                action = "ASK";
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", action);
            body.put("text", text);
            body.put("phase", "ACTIVE");
            body.put("remainingSeconds", remaining);
            return body;
        } catch (RuntimeException ex) {
            log.warn("Interview next fallback: {}", ex.getMessage());
            sessionRepository.save(session);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", "ASK");
            body.put("text", fallbackFollowUp(schedule.getStyle()));
            body.put("phase", "ACTIVE");
            body.put("remainingSeconds", remaining);
            return body;
        }
    }

    public Map<String, Object> end(String sessionToken) {
        AiInterviewSession session = requireSession(sessionToken);
        if ("COMPLETED".equals(session.getStatus())) {
            return Map.of("status", "COMPLETED");
        }
        AiInterviewSchedule schedule = scheduleRepository.findById(session.getScheduleId())
                .orElseThrow(() -> notFound("Schedule not found."));
        Instant now = now();
        session.setStatus("COMPLETED");
        session.setCompletedAt(now);
        session.setLastHeartbeatAt(now);
        applyUsage(session, schedule, now);
        sessionRepository.save(session);
        applyEvaluation(session, schedule);
        sessionRepository.save(session);
        schedule.setStatus("COMPLETED");
        scheduleRepository.save(schedule);
        return Map.of("status", "COMPLETED");
    }

    public Map<String, Object> addSnapshot(String sessionToken, MultipartFile file, int offsetMinutes) {
        AiInterviewSession session = requireSession(sessionToken);
        if ("COMPLETED".equals(session.getStatus())) {
            throw alreadyCompleted();
        }
        if (file == null || file.isEmpty()) {
            throw bad("SNAPSHOT_REQUIRED", "A snapshot image is required.");
        }
        if (file.getSize() > MAX_SNAPSHOT_BYTES) {
            throw bad("SNAPSHOT_TOO_LARGE", "Snapshot is too large.");
        }
        if (offsetMinutes < 0) {
            throw bad("INVALID_OFFSET", "offsetMinutes must be zero or greater.");
        }
        String contentType = file.getContentType() == null ? "image/jpeg" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            contentType = "image/jpeg";
        }
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw bad("SNAPSHOT_UNREADABLE", "Could not read the snapshot.");
        }
        String encoded = Base64.getEncoder().encodeToString(bytes);
        List<InterviewSnapshot> snapshots = session.getSnapshots() == null
                ? new ArrayList<>() : new ArrayList<>(session.getSnapshots());
        snapshots.removeIf(existing -> existing.getOffsetMinutes() != null
                && existing.getOffsetMinutes() == offsetMinutes);
        InterviewSnapshot snapshot = InterviewSnapshot.builder()
                .id(UUID.randomUUID().toString())
                .offsetMinutes(offsetMinutes)
                .contentType(contentType)
                .imageBase64(encoded)
                .capturedAt(now())
                .build();
        snapshots.add(snapshot);
        session.setSnapshots(snapshots);
        session.setLastHeartbeatAt(now());
        sessionRepository.save(session);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", snapshot.getId());
        body.put("offsetMinutes", offsetMinutes);
        return body;
    }

    private Map<String, Object> resumeOrReject(
            AiInterviewSchedule schedule, AiInterviewSession session, String clientInstanceId, Instant now) {
        if ("COMPLETED".equals(session.getStatus()) || "COMPLETED".equals(schedule.getStatus())) {
            throw alreadyCompleted();
        }
        boolean owner = clientInstanceId.equals(session.getClientInstanceId());
        boolean stale = InterviewSessionRules.lockIsStale(session.getLastHeartbeatAt(), now);
        String mode = InterviewSessionRules.resolveLock(session.getStatus(), owner, stale);
        if ("REJECT_PARALLEL".equals(mode)) {
            throw new InterviewFlowException("SESSION_LOCKED_ELSEWHERE", HttpStatus.CONFLICT, lockMessage());
        }
        if ("RECLAIM".equals(mode)) {
            session.setSessionToken(newSessionToken());
            session.setClientInstanceId(clientInstanceId);
        }
        session.setLastHeartbeatAt(now);
        if ("WINDING_DOWN".equals(session.getStatus())) {
            schedule.setStatus("WINDING_DOWN");
        } else {
            session.setStatus("IN_PROGRESS");
            schedule.setStatus("IN_PROGRESS");
        }
        sessionRepository.save(session);
        scheduleRepository.save(schedule);
        String opening = lastUnansweredAiText(session.getTurns());
        return joinView(session, opening, now);
    }

    private void markWindingDown(AiInterviewSchedule schedule, AiInterviewSession session) {
        session.setStatus("WINDING_DOWN");
        schedule.setStatus("WINDING_DOWN");
        sessionRepository.save(session);
        scheduleRepository.save(schedule);
    }

    private void applyUsage(AiInterviewSession session, AiInterviewSchedule schedule, Instant completedAt) {
        Instant started = session.getStartedAt() == null ? completedAt : session.getStartedAt();
        long seconds = Math.max(0, Duration.between(started, completedAt).getSeconds());
        int duration = schedule.getDurationMinutes() == null
                ? InterviewSessionRules.DEFAULT_DURATION_MINUTES : schedule.getDurationMinutes();
        long cap = duration * 60L;
        if (seconds > cap) {
            seconds = cap;
        }
        double minutes = Math.ceil((seconds / 60.0) * 100.0) / 100.0;
        session.setMinutesSpent(minutes);
        session.setCreditEstimate(round2(minutes * InterviewSessionRules.CREDITS_PER_MINUTE));
    }

    private void applyEvaluation(AiInterviewSession session, AiInterviewSchedule schedule) {
        try {
            Map<String, Object> response = skillamaAiClient.interviewEvaluate(aiContext(schedule, session.getTurns()));
            session.setScore(intOrNull(response.get("score")));
            String feedback = stringVal(response.get("feedback"));
            session.setFeedback(feedback == null || feedback.isBlank()
                    ? "Evaluation is temporarily unavailable." : feedback);
        } catch (RuntimeException ex) {
            log.warn("Interview evaluation fallback: {}", ex.getMessage());
            session.setFeedback("Evaluation is temporarily unavailable.");
        }
    }

    private String resolveOpening(AiInterviewSchedule schedule) {
        try {
            Map<String, Object> response = skillamaAiClient.interviewStart(aiContext(schedule, List.of()));
            String action = stringVal(response.get("action"));
            String text = stringVal(response.get("text"));
            if (text != null && !text.isBlank() && (action == null || !"CLOSE".equalsIgnoreCase(action))) {
                return text;
            }
        } catch (RuntimeException ex) {
            log.warn("Interview opening fallback: {}", ex.getMessage());
        }
        return fallbackOpening(schedule);
    }

    private Map<String, Object> aiContext(AiInterviewSchedule schedule, List<InterviewTurn> turns) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("style", schedule.getStyle());
        body.put("jdText", schedule.getJdText() == null ? "" : schedule.getJdText());
        body.put("course", schedule.getCourseName() == null ? "" : schedule.getCourseName());
        body.put("courseId", schedule.getCourseId());
        List<Map<String, Object>> questions = new ArrayList<>();
        for (InterviewSeedQuestion seed : copySeeds(schedule.getSeedQuestions())) {
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("id", seed.getId());
            question.put("text", seed.getText());
            question.put("tag", seed.getTag());
            questions.add(question);
        }
        body.put("questions", questions);
        body.put("transcript", turnViews(turns));
        return body;
    }

    private void refreshScheduleStatus(AiInterviewSchedule schedule, Instant now) {
        if (schedule.getStatus() == null
                || Set.of("RESCHEDULED", "CANCELLED", "COMPLETED", "EXPIRED").contains(schedule.getStatus())) {
            return;
        }
        AiInterviewSession session = sessionRepository.findByScheduleId(schedule.getId()).orElse(null);
        if (session != null) {
            if ("COMPLETED".equals(session.getStatus()) && !"COMPLETED".equals(schedule.getStatus())) {
                schedule.setStatus("COMPLETED");
                scheduleRepository.save(schedule);
            } else if ("WINDING_DOWN".equals(session.getStatus()) && !"WINDING_DOWN".equals(schedule.getStatus())) {
                schedule.setStatus("WINDING_DOWN");
                scheduleRepository.save(schedule);
            } else if ("IN_PROGRESS".equals(session.getStatus()) && !"IN_PROGRESS".equals(schedule.getStatus())) {
                schedule.setStatus("IN_PROGRESS");
                scheduleRepository.save(schedule);
            }
            return;
        }
        if (!"SCHEDULED".equals(schedule.getStatus()) && !"JOINABLE".equals(schedule.getStatus())) {
            return;
        }
        Instant slotEnd = InterviewSessionRules.endsAt(schedule.getScheduledAt(),
                schedule.getDurationMinutes() == null
                        ? InterviewSessionRules.DEFAULT_DURATION_MINUTES : schedule.getDurationMinutes());
        int grace = schedule.getJoinGraceMinutes() == null
                ? InterviewSessionRules.DEFAULT_JOIN_GRACE_MINUTES : schedule.getJoinGraceMinutes();
        String denial = InterviewSessionRules.firstJoinDenial(schedule.getScheduledAt(), grace, slotEnd, now);
        if (denial == null && !"JOINABLE".equals(schedule.getStatus())) {
            schedule.setStatus("JOINABLE");
            scheduleRepository.save(schedule);
        } else if (denial != null && !"TOO_EARLY".equals(denial) && !"INVALID_SCHEDULE".equals(denial)) {
            schedule.setStatus("EXPIRED");
            scheduleRepository.save(schedule);
        }
    }

    private void assertNoLiveSchedule(String email, String exceptId) {
        List<AiInterviewSchedule> live = scheduleRepository.findByCandidateEmailAndStatusIn(email, LIVE_SCHEDULE);
        for (AiInterviewSchedule schedule : live) {
            if (exceptId == null || !exceptId.equals(schedule.getId())) {
                throw new InterviewFlowException(
                        "EMAIL_ALREADY_SCHEDULED",
                        HttpStatus.CONFLICT,
                        "This email already has a live interview. Reschedule that one or wait until it finishes.");
            }
        }
    }

    private String resolveOrganizationId(Map<String, Object> body, User actor, String email) {
        String requested = blankToNull(stringVal(body.get("organizationId")));
        if (requested != null) {
            organizationRepository.findById(requested).orElseThrow(() -> notFound("Organization not found."));
            return requested;
        }
        User candidate = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (candidate != null && candidate.getOrganizationId() != null && !candidate.getOrganizationId().isBlank()) {
            return candidate.getOrganizationId();
        }
        if (actor.getOrganizationId() != null && !actor.getOrganizationId().isBlank()) {
            return actor.getOrganizationId();
        }
        return null;
    }

    private List<InterviewSeedQuestion> loadSeeds(List<String> questionIds) {
        List<InterviewSeedQuestion> seeds = new ArrayList<>();
        for (String id : questionIds) {
            AiInterviewQuestion question = questionRepository.findById(id)
                    .orElseThrow(() -> notFound("Question not found: " + id));
            seeds.add(InterviewSeedQuestion.builder()
                    .id(question.getId())
                    .text(question.getText())
                    .tag(question.getTag())
                    .build());
        }
        return seeds;
    }

    private boolean sendInvite(AiInterviewSchedule schedule) {
        try {
            Instant end = InterviewSessionRules.endsAt(schedule.getScheduledAt(), schedule.getDurationMinutes());
            return interviewInviteMailer.sendInvite(
                    schedule.getCandidateEmail(),
                    schedule.getInviteToken(),
                    schedule.getScheduledAt(),
                    end,
                    organizationName(schedule.getOrganizationId()));
        } catch (RuntimeException ex) {
            log.warn("Interview invite was saved but email failed: {}", ex.getMessage());
            return false;
        }
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

    private AiInterviewSession requireSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new InterviewFlowException(
                    "SESSION_REQUIRED", HttpStatus.UNAUTHORIZED, "Interview session is missing.");
        }
        return sessionRepository.findBySessionToken(sessionToken.trim())
                .orElseThrow(() -> new InterviewFlowException(
                        "SESSION_NOT_FOUND", HttpStatus.UNAUTHORIZED, "Interview session is no longer valid."));
    }

    private Map<String, Object> candidateView(AiInterviewSchedule schedule) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", schedule.getId());
        body.put("scheduleId", schedule.getId());
        body.put("title", titleFor(schedule.getStyle()));
        body.put("style", schedule.getStyle());
        body.put("status", schedule.getStatus());
        body.put("scheduledAt", schedule.getScheduledAt());
        body.put("durationMinutes", schedule.getDurationMinutes());
        body.put("inviteToken", schedule.getInviteToken());
        return body;
    }

    private Map<String, Object> scheduleView(AiInterviewSchedule schedule) {
        Map<String, Object> body = candidateView(schedule);
        body.put("candidateEmail", schedule.getCandidateEmail());
        body.put("organizationId", schedule.getOrganizationId());
        body.put("courseId", schedule.getCourseId());
        body.put("joinGraceMinutes", schedule.getJoinGraceMinutes());
        body.put("emailSent", Boolean.TRUE.equals(schedule.getEmailSent()));
        body.put("previousScheduleId", schedule.getPreviousScheduleId());
        return body;
    }

    private Map<String, Object> questionView(AiInterviewQuestion question) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", question.getId());
        body.put("text", question.getText());
        body.put("tag", question.getTag());
        body.put("organizationId", question.getOrganizationId());
        return body;
    }

    private Map<String, Object> joinView(AiInterviewSession session, String openingQuestion, Instant now) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionToken", session.getSessionToken());
        body.put("sessionId", session.getId());
        body.put("endsAtMs", session.getEndsAt() == null ? null : session.getEndsAt().toEpochMilli());
        body.put("startedAtMs", session.getStartedAt() == null ? null : session.getStartedAt().toEpochMilli());
        body.put("remainingSeconds", InterviewSessionRules.remainingSeconds(session.getEndsAt(), now));
        body.put("status", session.getStatus());
        if (openingQuestion != null && !openingQuestion.isBlank()) {
            body.put("openingQuestion", openingQuestion);
        }
        return body;
    }

    private Map<String, Object> clockView(AiInterviewSession session, Instant now) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", session.getStatus());
        body.put("endsAtMs", session.getEndsAt() == null ? null : session.getEndsAt().toEpochMilli());
        body.put("remainingSeconds", InterviewSessionRules.remainingSeconds(session.getEndsAt(), now));
        return body;
    }

    private Map<String, Object> closeView() {
        return closeView(InterviewSessionRules.CLOSE_TEXT);
    }

    private Map<String, Object> closeView(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", "CLOSE");
        body.put("text", text);
        body.put("phase", "CLOSING");
        body.put("remainingSeconds", 0);
        return body;
    }

    private List<Map<String, Object>> turnViews(List<InterviewTurn> turns) {
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

    private List<Map<String, Object>> snapshotViews(List<InterviewSnapshot> snapshots) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (snapshots == null) {
            return out;
        }
        List<InterviewSnapshot> ordered = new ArrayList<>(snapshots);
        ordered.sort((a, b) -> Integer.compare(
                a.getOffsetMinutes() == null ? 0 : a.getOffsetMinutes(),
                b.getOffsetMinutes() == null ? 0 : b.getOffsetMinutes()));
        for (InterviewSnapshot snapshot : ordered) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", snapshot.getId());
            row.put("offsetMinutes", snapshot.getOffsetMinutes());
            String type = snapshot.getContentType() == null ? "image/jpeg" : snapshot.getContentType();
            if (snapshot.getImageBase64() != null) {
                row.put("url", "data:" + type + ";base64," + snapshot.getImageBase64());
                row.put("imageUrl", row.get("url"));
            }
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> rescheduleViews(List<InterviewRescheduleEvent> events) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (events == null) {
            return out;
        }
        for (InterviewRescheduleEvent event : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", event.getFrom());
            row.put("to", event.getTo());
            row.put("reason", event.getReason());
            row.put("at", event.getAt());
            out.add(row);
        }
        return out;
    }

    private void appendTurn(AiInterviewSession session, String role, String text) {
        List<InterviewTurn> turns = session.getTurns() == null
                ? new ArrayList<>() : new ArrayList<>(session.getTurns());
        turns.add(InterviewTurn.builder().role(role).text(text).at(now()).build());
        session.setTurns(turns);
    }

    private String lastUnansweredAiText(List<InterviewTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return null;
        }
        InterviewTurn last = turns.get(turns.size() - 1);
        if ("AI".equals(last.getRole())) {
            return last.getText();
        }
        return null;
    }

    private String organizationName(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return null;
        }
        return organizationRepository.findById(organizationId).map(Organization::getName).orElse(null);
    }

    private String resolveCourseName(String courseId) {
        if (courseId == null) {
            return null;
        }
        return courseRepository.findById(courseId).map(Course::getName).orElse(null);
    }

    private String fallbackOpening(AiInterviewSchedule schedule) {
        if (schedule.getSeedQuestions() != null) {
            for (InterviewSeedQuestion seed : schedule.getSeedQuestions()) {
                if (seed.getText() != null && !seed.getText().isBlank()) {
                    return seed.getText();
                }
            }
        }
        return switch (schedule.getStyle() == null ? "MIXED" : schedule.getStyle()) {
            case "BEHAVIORAL" -> "Tell me about a time you worked through a disagreement with a teammate.";
            case "TECHNICAL" -> "Walk me through how you would design a small service for this role.";
            default -> "Let's begin with a short introduction and the work you are most proud of.";
        };
    }

    private String fallbackFollowUp(String style) {
        if ("TECHNICAL".equals(style)) {
            return "What trade-off did you consider, and why did you choose that approach?";
        }
        if ("BEHAVIORAL".equals(style)) {
            return "What was your specific contribution, and what would you do differently next time?";
        }
        return "Can you go one level deeper on what you just described?";
    }

    private String titleFor(String style) {
        return switch (style == null ? "" : style) {
            case "BEHAVIORAL" -> "Behavioral interview";
            case "TECHNICAL" -> "Technical interview";
            default -> "Mixed interview";
        };
    }

    private InterviewFlowException windowError(String denial) {
        return switch (denial) {
            case "TOO_EARLY" -> bad("TOO_EARLY",
                    "This interview has not started yet. Please join at the scheduled time.");
            case "JOIN_GRACE_EXPIRED" -> bad("JOIN_GRACE_EXPIRED",
                    "The join window has closed. Ask your administrator to reschedule.");
            case "SLOT_EXPIRED" -> bad("SLOT_EXPIRED", "This interview invite has expired.");
            default -> bad("INVALID_SCHEDULE", "This interview invite is not valid.");
        };
    }

    private InterviewFlowException alreadyCompleted() {
        return new InterviewFlowException(
                "ALREADY_COMPLETED",
                HttpStatus.CONFLICT,
                "This interview is already complete. Ask your administrator to reschedule.");
    }

    private String lockMessage() {
        return "This interview is already in progress on another device or tab. "
                + "Return to that session, or wait a couple of minutes if it disconnected.";
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String newInviteToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String newSessionToken() {
        return UUID.randomUUID().toString();
    }

    private static InterviewFlowException bad(String code, String message) {
        return new InterviewFlowException(code, HttpStatus.BAD_REQUEST, message);
    }

    private static InterviewFlowException notFound(String message) {
        return new InterviewFlowException("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(Object value, String message) {
        String text = stringVal(value);
        if (text == null || text.isBlank()) {
            throw bad("VALIDATION", message);
        }
        return text.trim();
    }

    private static String requireStyle(Object value, String label) {
        String style = value == null ? "" : value.toString().trim().toUpperCase(Locale.ROOT);
        if (!STYLES.contains(style)) {
            throw bad("INVALID_STYLE", label + " must be BEHAVIORAL, TECHNICAL, or MIXED.");
        }
        return style;
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static int positiveInt(Object value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ex) {
                throw bad("VALIDATION", "Expected a number.");
            }
        }
        if (parsed < min || parsed > max) {
            throw bad("VALIDATION", "Value must be between " + min + " and " + max + ".");
        }
        return parsed;
    }

    private static Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Instant parseInstant(Object value, String message) {
        Instant parsed = parseInstantOrNull(value);
        if (parsed == null) {
            throw bad("VALIDATION", message);
        }
        return parsed;
    }

    private static Instant parseOptionalInstant(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ignored) {
            try {
                LocalDate date = LocalDate.parse(value.trim());
                return endOfDay
                        ? date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1)
                        : date.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (Exception ex) {
                throw bad("VALIDATION", "from and to must be ISO-8601 timestamps or dates.");
            }
        }
    }

    private static Instant parseInstantOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            throw bad("VALIDATION", "scheduledAt must be an ISO-8601 timestamp.");
        }
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : collection) {
            if (item != null && !item.toString().isBlank()) {
                out.add(item.toString());
            }
        }
        return out;
    }

    private static List<String> copyStrings(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static List<InterviewSeedQuestion> copySeeds(List<InterviewSeedQuestion> seeds) {
        if (seeds == null) {
            return new ArrayList<>();
        }
        List<InterviewSeedQuestion> copy = new ArrayList<>();
        for (InterviewSeedQuestion seed : seeds) {
            copy.add(InterviewSeedQuestion.builder()
                    .id(seed.getId())
                    .text(seed.getText())
                    .tag(seed.getTag())
                    .build());
        }
        return copy;
    }

    private static List<InterviewRescheduleEvent> appendEvent(
            List<InterviewRescheduleEvent> existing, InterviewRescheduleEvent event) {
        List<InterviewRescheduleEvent> copy = existing == null
                ? new ArrayList<>() : new ArrayList<>(existing);
        copy.add(event);
        return copy;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
