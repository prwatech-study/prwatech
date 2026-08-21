package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AiAnswerFeedbackRequestDTO;
import com.prwatech.skillama.model.AiAnswerFeedback;
import com.prwatech.skillama.repository.AiAnswerFeedbackRepository;
import com.prwatech.skillama.util.IndiaTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAnswerFeedbackServiceTest {

    @Mock private AiAnswerFeedbackRepository repository;

    private AiAnswerFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new AiAnswerFeedbackService(repository);
        when(repository.save(any(AiAnswerFeedback.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AiAnswerFeedbackRequestDTO request(String messageId, Boolean helpful) {
        AiAnswerFeedbackRequestDTO dto = new AiAnswerFeedbackRequestDTO();
        dto.setMessageId(messageId);
        dto.setHelpful(helpful);
        return dto;
    }

    private AiAnswerFeedback vote(boolean helpful, LocalDateTime createdAt) {
        AiAnswerFeedback feedback = new AiAnswerFeedback();
        feedback.setHelpful(helpful);
        feedback.setCreatedAt(createdAt);
        return feedback;
    }

    // ---------- submit ----------

    @Test
    void submitRequiresMessageIdAndHelpful() {
        assertThrows(IllegalArgumentException.class, () -> service.submit("u1", request(null, true)));
        assertThrows(IllegalArgumentException.class, () -> service.submit("u1", request("  ", true)));
        assertThrows(IllegalArgumentException.class, () -> service.submit("u1", request("m1", null)));
        assertThrows(IllegalArgumentException.class, () -> service.submit("u1", null));
    }

    @Test
    void submitCreatesVoteWithDefaultEndpoint() {
        when(repository.findByUserIdAndMessageId("u1", "m1")).thenReturn(Optional.empty());

        AiAnswerFeedback saved = service.submit("u1", request("m1", true));

        assertEquals("u1", saved.getUserId());
        assertEquals("m1", saved.getMessageId());
        assertTrue(saved.isHelpful());
        assertEquals("chat_ask", saved.getEndpoint());
    }

    @Test
    void reVotingOverwritesInsteadOfDuplicating() {
        AiAnswerFeedback existing = new AiAnswerFeedback();
        existing.setId("f1");
        existing.setUserId("u1");
        existing.setMessageId("m1");
        existing.setHelpful(true);
        existing.setCreatedAt(IndiaTime.now().minusMinutes(5));
        when(repository.findByUserIdAndMessageId("u1", "m1")).thenReturn(Optional.of(existing));

        AiAnswerFeedback saved = service.submit("u1", request("m1", false));

        assertEquals("f1", saved.getId());
        assertFalse(saved.isHelpful());
    }

    // ---------- helpful rate ----------

    @Test
    void helpfulRateNullWhenNoVotes() {
        when(repository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
        AiAnswerFeedbackService.HelpfulRate rate =
                service.helpfulRate(IndiaTime.now().minusDays(30), IndiaTime.now());
        assertEquals(0, rate.totalVotes());
        assertNull(rate.helpfulRatePercent());
    }

    @Test
    void helpfulRateComputesPercentage() {
        LocalDateTime now = IndiaTime.now();
        when(repository.findByCreatedAtBetween(any(), any())).thenReturn(List.of(
                vote(true, now), vote(true, now), vote(false, now)));

        AiAnswerFeedbackService.HelpfulRate rate = service.helpfulRate(now.minusDays(30), now);

        assertEquals(3, rate.totalVotes());
        assertEquals(2, rate.helpfulVotes());
        assertEquals(66.7, rate.helpfulRatePercent());
    }
}
