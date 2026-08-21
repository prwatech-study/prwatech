package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AiAnswerFeedbackRequestDTO;
import com.prwatech.skillama.model.AiAnswerFeedback;
import com.prwatech.skillama.repository.AiAnswerFeedbackRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAnswerFeedbackService {

    private final AiAnswerFeedbackRepository aiAnswerFeedbackRepository;

    /** Upserts the learner's vote for one AI answer — re-rating overwrites the previous vote. */
    public AiAnswerFeedback submit(String userId, AiAnswerFeedbackRequestDTO request) {
        if (request == null || request.getMessageId() == null || request.getMessageId().isBlank()) {
            throw new IllegalArgumentException("messageId is required");
        }
        if (request.getHelpful() == null) {
            throw new IllegalArgumentException("helpful is required");
        }
        AiAnswerFeedback feedback = aiAnswerFeedbackRepository
                .findByUserIdAndMessageId(userId, request.getMessageId())
                .orElseGet(AiAnswerFeedback::new);
        feedback.setUserId(userId);
        feedback.setMessageId(request.getMessageId());
        feedback.setCourseId(request.getCourseId());
        feedback.setEndpoint(request.getEndpoint() != null && !request.getEndpoint().isBlank()
                ? request.getEndpoint()
                : "chat_ask");
        feedback.setHelpful(request.getHelpful());
        if (feedback.getCreatedAt() == null) {
            feedback.setCreatedAt(IndiaTime.now());
        }
        feedback.setUpdatedAt(IndiaTime.now());
        return aiAnswerFeedbackRepository.save(feedback);
    }

    /** Helpful percentage (0-100) over votes in the window; null when there are no votes yet. */
    public HelpfulRate helpfulRate(LocalDateTime start, LocalDateTime end) {
        List<AiAnswerFeedback> votes = aiAnswerFeedbackRepository.findByCreatedAtBetween(start, end);
        long total = votes.size();
        if (total == 0) {
            return new HelpfulRate(0, 0, null);
        }
        long helpful = votes.stream().filter(AiAnswerFeedback::isHelpful).count();
        double rate = Math.round(helpful * 1000.0 / total) / 10.0;
        return new HelpfulRate(total, helpful, rate);
    }

    public long totalVotes() {
        return aiAnswerFeedbackRepository.count();
    }

    public record HelpfulRate(long totalVotes, long helpfulVotes, Double helpfulRatePercent) {}
}
