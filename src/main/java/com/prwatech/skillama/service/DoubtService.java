package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.AdminAiMentorDoubtDTO;
import com.prwatech.skillama.dto.AiAnswerFeedbackRequestDTO;
import com.prwatech.skillama.dto.AiQueryReplyDTO;
import com.prwatech.skillama.dto.AskDoubtRequestDTO;
import com.prwatech.skillama.dto.DoubtFeedbackRequestDTO;
import com.prwatech.skillama.dto.DoubtFollowUpRequestDTO;
import com.prwatech.skillama.dto.DoubtMessageDTO;
import com.prwatech.skillama.dto.DoubtResponseDTO;
import com.prwatech.skillama.dto.ProxiedAudioDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.Doubt;
import com.prwatech.skillama.model.DoubtStatus;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.DoubtRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import com.prwatech.skillama.util.PiiRedactor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** AI Mentor doubts: each user question is its own countable Doubt record. */
@Service
@RequiredArgsConstructor
public class DoubtService {

    private static final String DEFAULT_COURSE_NAME = "Python";

    private final DoubtRepository doubtRepository;
    private final SkillamaUserRepository userRepository;
    private final CourseRepository courseRepository;
    private final SkillamaAiClient skillamaAiClient;
    private final AiAnswerFeedbackService aiAnswerFeedbackService;

    @Transactional
    public DoubtResponseDTO askDoubt(String userId, AskDoubtRequestDTO request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("question is required");
        }
        if (request.getCourseId() == null || request.getCourseId().isBlank()) {
            throw new IllegalArgumentException("courseId is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String courseName = courseRepository.findById(request.getCourseId())
                .map(Course::getName)
                .orElse(DEFAULT_COURSE_NAME);
        String question = PiiRedactor.redact(request.getQuestion());

        AiQueryReplyDTO reply = skillamaAiClient.answerQuery(
                user, "ai_mentor_ask", request.getCourseId(),
                question, "General " + courseName, courseName, List.of(), null);

        var now = IndiaTime.now();
        List<Doubt.DoubtMessage> messages = new ArrayList<>();
        messages.add(Doubt.DoubtMessage.builder()
                .id(UUID.randomUUID().toString())
                .sender(Doubt.Sender.USER)
                .content(question)
                .timestamp(now)
                .build());
        String answer = PiiRedactor.redact(reply.getResponseText() != null ? reply.getResponseText() : "");
        if (!answer.isBlank()) {
            messages.add(Doubt.DoubtMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .sender(Doubt.Sender.AI)
                    .content(answer)
                    .audioUrl(reply.getAudioUrl())
                    .timestamp(now)
                    .build());
        }

        Doubt doubt = Doubt.builder()
                .userId(userId)
                .courseId(request.getCourseId())
                .moduleId(request.getModuleId())
                .lessonId(request.getLessonId())
                .status(DoubtStatus.PENDING)
                .messages(messages)
                .createdAt(now)
                .updatedAt(now)
                .build();
        doubtRepository.save(doubt);
        return toResponseDto(doubt);
    }

    @Transactional
    public DoubtResponseDTO addFollowUp(String userId, String doubtId, DoubtFollowUpRequestDTO request) {
        Doubt doubt = requireOwnedDoubt(userId, doubtId);
        if (request == null
                || ((request.getQuestion() == null || request.getQuestion().isBlank())
                    && (request.getNudgeType() == null || request.getNudgeType().isBlank()))) {
            throw new IllegalArgumentException("question or nudgeType is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String courseName = doubt.getCourseId() != null
                ? courseRepository.findById(doubt.getCourseId()).map(Course::getName).orElse(DEFAULT_COURSE_NAME)
                : DEFAULT_COURSE_NAME;
        String userContent = request.getQuestion() != null && !request.getQuestion().isBlank()
                ? PiiRedactor.redact(request.getQuestion())
                : request.getNudgeType();
        String queryText = request.getQuery() != null && !request.getQuery().isBlank()
                ? request.getQuery()
                : userContent;

        AiQueryReplyDTO reply = skillamaAiClient.answerQuery(
                user, "ai_mentor_follow_up", doubt.getCourseId(),
                queryText, "General " + courseName, courseName, List.of(), null);

        var now = IndiaTime.now();
        doubt.getMessages().add(Doubt.DoubtMessage.builder()
                .id(UUID.randomUUID().toString())
                .sender(Doubt.Sender.USER)
                .content(userContent)
                .nudgeType(request.getNudgeType())
                .timestamp(now)
                .build());
        String answer = PiiRedactor.redact(reply.getResponseText() != null ? reply.getResponseText() : "");
        if (!answer.isBlank()) {
            doubt.getMessages().add(Doubt.DoubtMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .sender(Doubt.Sender.AI)
                    .content(answer)
                    .audioUrl(reply.getAudioUrl())
                    .nudgeType(request.getNudgeType())
                    .timestamp(now)
                    .build());
        }
        doubt.setUpdatedAt(now);
        doubtRepository.save(doubt);
        return toResponseDto(doubt);
    }

    @Transactional
    public DoubtResponseDTO submitFeedback(String userId, String doubtId, DoubtFeedbackRequestDTO request) {
        Doubt doubt = requireOwnedDoubt(userId, doubtId);
        if (request == null || request.getMessageId() == null) {
            throw new IllegalArgumentException("messageId is required");
        }
        Doubt.DoubtMessage message = doubt.getMessages().stream()
                .filter(m -> request.getMessageId().equals(m.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Message not found"));
        message.setHelpful(request.getHelpful());
        if (Boolean.FALSE.equals(request.getHelpful())) {
            doubt.setStatus(DoubtStatus.NEEDS_MENTOR);
        } else if (Boolean.TRUE.equals(request.getHelpful())
                && (doubt.getStatus() == DoubtStatus.PENDING || doubt.getStatus() == DoubtStatus.NEEDS_MENTOR)
                && doubt.getMessages().stream().noneMatch(m -> Boolean.FALSE.equals(m.getHelpful()))) {
            // A thumbs-up un-escalates only once no answer in the thread is still voted
            // unhelpful; RESOLVED (mentor-closed) is never downgraded by a vote.
            doubt.setStatus(DoubtStatus.SOLVED);
        }
        doubt.setUpdatedAt(IndiaTime.now());
        doubtRepository.save(doubt);

        // Mirror the vote into the unified ai_answer_feedback collection so AI-Mentor
        // ratings count toward the investor dashboard's "AI helpful rate" alongside chat.
        if (request.getHelpful() != null) {
            try {
                AiAnswerFeedbackRequestDTO unified = new AiAnswerFeedbackRequestDTO();
                unified.setMessageId(request.getMessageId());
                unified.setCourseId(doubt.getCourseId());
                unified.setEndpoint("ai_mentor_ask");
                unified.setHelpful(request.getHelpful());
                aiAnswerFeedbackService.submit(userId, unified);
            } catch (Exception e) {
                // Metric mirror must never break the primary doubt-feedback flow.
            }
        }
        return toResponseDto(doubt);
    }

    @Transactional
    public DoubtResponseDTO updateStatus(String userId, String doubtId, DoubtStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        Doubt doubt = requireOwnedDoubt(userId, doubtId);
        doubt.setStatus(status);
        var now = IndiaTime.now();
        doubt.setUpdatedAt(now);
        if (status == DoubtStatus.RESOLVED) {
            doubt.setResolvedAt(now);
        }
        doubtRepository.save(doubt);
        return toResponseDto(doubt);
    }

    public List<DoubtResponseDTO> listMyDoubts(String userId, String courseId) {
        List<Doubt> doubts = (courseId == null || courseId.isBlank())
                ? doubtRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : doubtRepository.findByUserIdAndCourseIdOrderByCreatedAtDesc(userId, courseId);
        return doubts.stream().map(this::toResponseDto).collect(Collectors.toList());
    }

    public DoubtResponseDTO getDoubt(String userId, String doubtId) {
        return toResponseDto(requireOwnedDoubt(userId, doubtId));
    }

    /**
     * Proxies a message's spoken-answer audio — the raw ai-tutor URL is fetched here and
     * never handed to the client (see {@link SkillamaAiClient#fetchAudioBytes}).
     */
    public ProxiedAudioDTO getMessageAudio(String userId, String doubtId, String messageId) {
        Doubt doubt = requireOwnedDoubt(userId, doubtId);
        Doubt.DoubtMessage message = doubt.getMessages().stream()
                .filter(m -> messageId.equals(m.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if (message.getAudioUrl() == null || message.getAudioUrl().isBlank()) {
            throw new NotFoundException("No audio available for this message");
        }
        return skillamaAiClient.fetchAudioBytes(message.getAudioUrl());
    }

    /** Admin monitor: paginated AI Mentor doubts across all learners. */
    public Page<AdminAiMentorDoubtDTO> listAdminDoubts(
            int page, int size, String userId, String courseId, String email, DoubtStatus status) {
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

        List<AdminAiMentorDoubtDTO> rows = doubtRepository.findAll().stream()
                .filter(d -> userId == null || userId.isBlank() || userId.equals(d.getUserId()))
                .filter(d -> courseId == null || courseId.isBlank() || courseId.equals(d.getCourseId()))
                .filter(d -> status == null || status == d.getStatus())
                .filter(d -> allowedUserIdsFinal == null
                        || (d.getUserId() != null && allowedUserIdsFinal.contains(d.getUserId())))
                .map(d -> {
                    User user = d.getUserId() != null
                            ? userCache.computeIfAbsent(d.getUserId(), id -> userRepository.findById(id).orElse(null))
                            : null;
                    String courseName = d.getCourseId() != null
                            ? courseNameCache.computeIfAbsent(d.getCourseId(),
                                    cid -> courseRepository.findById(cid).map(Course::getName).orElse(null))
                            : null;
                    List<Doubt.DoubtMessage> messages = d.getMessages() != null ? d.getMessages() : List.of();
                    String question = messages.stream()
                            .filter(m -> m.getSender() == Doubt.Sender.USER)
                            .map(Doubt.DoubtMessage::getContent)
                            .findFirst()
                            .orElse(null);
                    String latestAnswer = messages.stream()
                            .filter(m -> m.getSender() == Doubt.Sender.AI)
                            .reduce((first, second) -> second)
                            .map(Doubt.DoubtMessage::getContent)
                            .orElse(null);
                    return AdminAiMentorDoubtDTO.builder()
                            .doubtId(d.getId())
                            .userId(d.getUserId())
                            .userName(user != null ? user.getName() : null)
                            .userEmail(user != null ? user.getEmail() : null)
                            .courseId(d.getCourseId())
                            .courseName(courseName)
                            .moduleId(d.getModuleId())
                            .lessonId(d.getLessonId())
                            .status(d.getStatus())
                            .question(question)
                            .latestAnswer(latestAnswer)
                            .messageCount(messages.size())
                            .createdAt(d.getCreatedAt())
                            .updatedAt(d.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        rows.sort(Comparator.comparing(
                AdminAiMentorDoubtDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int total = rows.size();
        int from = pageNum * limit;
        if (from >= total) {
            return new PageImpl<>(List.of(), PageRequest.of(pageNum, limit), total);
        }
        int to = Math.min(from + limit, total);
        return new PageImpl<>(rows.subList(from, to), PageRequest.of(pageNum, limit), total);
    }

    private Doubt requireOwnedDoubt(String userId, String doubtId) {
        Doubt doubt = doubtRepository.findById(doubtId)
                .orElseThrow(() -> new NotFoundException("Doubt not found"));
        if (userId == null || !userId.equals(doubt.getUserId())) {
            throw new NotFoundException("Doubt not found");
        }
        return doubt;
    }

    private DoubtResponseDTO toResponseDto(Doubt doubt) {
        List<DoubtMessageDTO> messages = doubt.getMessages() == null
                ? List.of()
                : doubt.getMessages().stream()
                        .map(m -> DoubtMessageDTO.builder()
                                .id(m.getId())
                                .sender(m.getSender() != null ? m.getSender().name() : null)
                                .content(m.getContent())
                                .hasAudio(m.getAudioUrl() != null && !m.getAudioUrl().isBlank())
                                .nudgeType(m.getNudgeType())
                                .helpful(m.getHelpful())
                                .timestamp(m.getTimestamp())
                                .build())
                        .collect(Collectors.toList());
        return DoubtResponseDTO.builder()
                .id(doubt.getId())
                .courseId(doubt.getCourseId())
                .moduleId(doubt.getModuleId())
                .lessonId(doubt.getLessonId())
                .status(doubt.getStatus())
                .messages(messages)
                .createdAt(doubt.getCreatedAt())
                .updatedAt(doubt.getUpdatedAt())
                .resolvedAt(doubt.getResolvedAt())
                .build();
    }
}
