package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.AskDoubtRequestDTO;
import com.prwatech.skillama.dto.DoubtFeedbackRequestDTO;
import com.prwatech.skillama.dto.DoubtFollowUpRequestDTO;
import com.prwatech.skillama.dto.DoubtResponseDTO;
import com.prwatech.skillama.dto.ProxiedAudioDTO;
import com.prwatech.skillama.model.Doubt;
import com.prwatech.skillama.model.DoubtStatus;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.DoubtRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoubtServiceTest {

    @Mock private DoubtRepository doubtRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private SkillamaAiClient skillamaAiClient;

    @InjectMocks private DoubtService doubtService;

    @Test
    void askDoubt_createsPendingDoubtWithQuestionAndAnswerMessages() {
        AskDoubtRequestDTO request = new AskDoubtRequestDTO();
        request.setCourseId("course-1");
        request.setModuleId("module-1");
        request.setLessonId("lesson-1");
        request.setQuestion("What is a DataFrame?");
        request.setAnswer("A DataFrame is a 2D labeled data structure.");
        request.setAnswerAudioUrl("https://cdn.example.com/answer-1.mp3");

        when(doubtRepository.save(any(Doubt.class))).thenAnswer(inv -> inv.getArgument(0));

        DoubtResponseDTO response = doubtService.askDoubt("user-1", request);

        assertEquals(DoubtStatus.PENDING, response.getStatus());
        assertEquals("course-1", response.getCourseId());
        assertEquals(2, response.getMessages().size());
        assertEquals("USER", response.getMessages().get(0).getSender());
        assertTrue(response.getMessages().get(1).getHasAudio());
        assertFalse(response.getMessages().get(0).getHasAudio());
        assertEquals("What is a DataFrame?", response.getMessages().get(0).getContent());
        assertEquals("AI", response.getMessages().get(1).getSender());
    }

    @Test
    void askDoubt_missingQuestion_throwsIllegalArgumentException() {
        AskDoubtRequestDTO request = new AskDoubtRequestDTO();
        request.setCourseId("course-1");

        assertThrows(IllegalArgumentException.class, () -> doubtService.askDoubt("user-1", request));
    }

    @Test
    void addFollowUp_appendsNudgeMessagesToExistingDoubt() {
        Doubt doubt = existingDoubt("doubt-1", "user-1");
        when(doubtRepository.findById("doubt-1")).thenReturn(Optional.of(doubt));
        when(doubtRepository.save(any(Doubt.class))).thenAnswer(inv -> inv.getArgument(0));

        DoubtFollowUpRequestDTO request = new DoubtFollowUpRequestDTO();
        request.setNudgeType("EXPLAIN_MORE");
        request.setAnswer("Here is a more detailed explanation.");
        request.setAnswerAudioUrl("https://cdn.example.com/answer-2.mp3");

        DoubtResponseDTO response = doubtService.addFollowUp("user-1", "doubt-1", request);

        assertEquals(4, response.getMessages().size());
        assertEquals("EXPLAIN_MORE", response.getMessages().get(2).getNudgeType());
        assertEquals("Here is a more detailed explanation.", response.getMessages().get(3).getContent());
        assertTrue(response.getMessages().get(3).getHasAudio());
    }

    // ---------- getMessageAudio ----------

    @Test
    void getMessageAudio_proxiesBytesWithoutExposingRawUrl() {
        Doubt doubt = existingDoubt("doubt-1", "user-1");
        String messageId = doubt.getMessages().get(1).getId();
        doubt.getMessages().get(1).setAudioUrl("https://ai.prwatech.com/get_audio/answer.mp3");
        when(doubtRepository.findById("doubt-1")).thenReturn(Optional.of(doubt));
        when(skillamaAiClient.fetchAudioBytes("https://ai.prwatech.com/get_audio/answer.mp3"))
                .thenReturn(ProxiedAudioDTO.builder().data(new byte[]{1, 2, 3}).contentType("audio/mpeg").build());

        ProxiedAudioDTO audio = doubtService.getMessageAudio("user-1", "doubt-1", messageId);

        assertEquals("audio/mpeg", audio.getContentType());
        assertEquals(3, audio.getData().length);
    }

    @Test
    void getMessageAudio_throwsWhenMessageHasNoAudio() {
        Doubt doubt = existingDoubt("doubt-1", "user-1");
        String messageId = doubt.getMessages().get(0).getId(); // user message, no audio
        when(doubtRepository.findById("doubt-1")).thenReturn(Optional.of(doubt));

        assertThrows(NotFoundException.class,
                () -> doubtService.getMessageAudio("user-1", "doubt-1", messageId));
    }

    @Test
    void getMessageAudio_rejectsWrongOwner() {
        Doubt doubt = existingDoubt("doubt-1", "someone-else");
        when(doubtRepository.findById("doubt-1")).thenReturn(Optional.of(doubt));

        assertThrows(NotFoundException.class,
                () -> doubtService.getMessageAudio("user-1", "doubt-1", "any-message-id"));
    }

    @Test
    void addFollowUp_doubtOwnedByAnotherUser_throwsNotFound() {
        Doubt doubt = existingDoubt("doubt-1", "someone-else");
        when(doubtRepository.findById("doubt-1")).thenReturn(Optional.of(doubt));

        DoubtFollowUpRequestDTO request = new DoubtFollowUpRequestDTO();
        request.setNudgeType("EXPLAIN_MORE");

        assertThrows(NotFoundException.class,
                () -> doubtService.addFollowUp("user-1", "doubt-1", request));
    }

    @Test
    void submitFeedback_helpfulTrue_marksSolved() {
        Doubt doubt = existingDoubt("doubt-1", "user-1");
        when(doubtRepository.findById("doubt-1")).thenReturn(Optional.of(doubt));
        when(doubtRepository.save(any(Doubt.class))).thenAnswer(inv -> inv.getArgument(0));

        DoubtFeedbackRequestDTO request = new DoubtFeedbackRequestDTO();
        request.setMessageId(doubt.getMessages().get(1).getId());
        request.setHelpful(true);

        DoubtResponseDTO response = doubtService.submitFeedback("user-1", "doubt-1", request);

        assertEquals(DoubtStatus.SOLVED, response.getStatus());
        assertTrue(response.getMessages().get(1).getHelpful());
    }

    @Test
    void submitFeedback_helpfulFalse_marksNeedsMentor() {
        Doubt doubt = existingDoubt("doubt-1", "user-1");
        when(doubtRepository.findById("doubt-1")).thenReturn(Optional.of(doubt));
        when(doubtRepository.save(any(Doubt.class))).thenAnswer(inv -> inv.getArgument(0));

        DoubtFeedbackRequestDTO request = new DoubtFeedbackRequestDTO();
        request.setMessageId(doubt.getMessages().get(1).getId());
        request.setHelpful(false);

        DoubtResponseDTO response = doubtService.submitFeedback("user-1", "doubt-1", request);

        assertEquals(DoubtStatus.NEEDS_MENTOR, response.getStatus());
    }

    @Test
    void updateStatus_toResolved_setsResolvedAt() {
        Doubt doubt = existingDoubt("doubt-1", "user-1");
        when(doubtRepository.findById("doubt-1")).thenReturn(Optional.of(doubt));
        ArgumentCaptor<Doubt> captor = ArgumentCaptor.forClass(Doubt.class);
        when(doubtRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        doubtService.updateStatus("user-1", "doubt-1", DoubtStatus.RESOLVED);

        assertNotNull(captor.getValue().getResolvedAt());
        assertEquals(DoubtStatus.RESOLVED, captor.getValue().getStatus());
    }

    @Test
    void listMyDoubts_filtersByCourseWhenProvided() {
        when(doubtRepository.findByUserIdAndCourseIdOrderByCreatedAtDesc("user-1", "course-1"))
                .thenReturn(List.of(existingDoubt("doubt-1", "user-1")));

        List<DoubtResponseDTO> doubts = doubtService.listMyDoubts("user-1", "course-1");

        assertEquals(1, doubts.size());
    }

    @Test
    void getDoubt_notOwnedByCaller_throwsNotFound() {
        Doubt doubt = existingDoubt("doubt-1", "someone-else");
        when(doubtRepository.findById("doubt-1")).thenReturn(Optional.of(doubt));

        assertThrows(NotFoundException.class, () -> doubtService.getDoubt("user-1", "doubt-1"));
    }

    private static Doubt existingDoubt(String id, String userId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 10, 0);
        return Doubt.builder()
                .id(id)
                .userId(userId)
                .courseId("course-1")
                .status(DoubtStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .messages(new java.util.ArrayList<>(List.of(
                        Doubt.DoubtMessage.builder()
                                .id(UUID.randomUUID().toString())
                                .sender(Doubt.Sender.USER)
                                .content("What is a DataFrame?")
                                .timestamp(now)
                                .build(),
                        Doubt.DoubtMessage.builder()
                                .id(UUID.randomUUID().toString())
                                .sender(Doubt.Sender.AI)
                                .content("A DataFrame is a 2D labeled data structure.")
                                .timestamp(now)
                                .build())))
                .build();
    }
}
