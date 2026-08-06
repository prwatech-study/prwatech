package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.AdminCodeAssistInteractionDTO;
import com.prwatech.skillama.dto.CodeAssistRequestDTO;
import com.prwatech.skillama.dto.CodeAssistResponseDTO;
import com.prwatech.skillama.dto.GeneratedCodeAssistDTO;
import com.prwatech.skillama.dto.ProxiedAudioDTO;
import com.prwatech.skillama.model.CodeAssistFeature;
import com.prwatech.skillama.model.CodeAssistInteraction;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CodeAssistInteractionRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeAssistServiceTest {

    @Mock private CodeAssistInteractionRepository interactionRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private SkillamaAiClient skillamaAiClient;
    @Mock private PracticalSandboxService practicalSandboxService;
    @Mock private PracticalDatasetService practicalDatasetService;

    @InjectMocks private CodeAssistService codeAssistService;

    private static User existingUser(String id) {
        User user = new User();
        user.setId(id);
        user.setEmail("learner@example.com");
        user.setName("Learner One");
        return user;
    }

    private static GeneratedCodeAssistDTO generated(String audioUrl) {
        return GeneratedCodeAssistDTO.builder()
                .codeOutput("Hello, world!")
                .correctedCode("print('Hello, world!')")
                .responseText("Your code runs fine.")
                .audioUrl(audioUrl)
                .subtitlePath("https://ai.prwatech.com/subtitles/1.srt")
                .modelId("claude-x")
                .inputTokens(10)
                .outputTokens(20)
                .totalTokens(30)
                .build();
    }

    // ---------- runDebug / runCodeExecution ----------

    @Test
    void runDebug_persistsInteractionAndRecordsUsage_hidesRawAudioUrl() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser("user-1")));
        when(courseRepository.findById("course-1"))
                .thenReturn(Optional.of(Course.builder().id("course-1").name("Python Basics").build()));
        // "Python Basics" matches the Python-course real-execution path; sandbox integration
        // itself isn't under test here, so simulate it being unavailable — CodeAssistService
        // degrades gracefully and calls ai-tutor with no real_output/real_error, same as before.
        when(practicalSandboxService.executeAdHoc(anyString()))
                .thenThrow(new IllegalStateException("sandbox not under test here"));
        when(skillamaAiClient.runCodeAssist(
                        any(User.class), eq("debug_assist"), eq("course-1"), eq("print(1)"), eq("Python Basics"),
                        isNull(), isNull(), isNull()))
                .thenReturn(generated("https://ai.prwatech.com/get_audio/explain.mp3"));
        when(interactionRepository.save(any(CodeAssistInteraction.class)))
                .thenAnswer(inv -> {
                    CodeAssistInteraction saved = inv.getArgument(0);
                    saved.setId("interaction-1");
                    return saved;
                });

        CodeAssistRequestDTO request = new CodeAssistRequestDTO();
        request.setCode("print(1)");
        request.setCourseId("course-1");

        CodeAssistResponseDTO response = codeAssistService.runDebug("user-1", request);

        assertEquals("interaction-1", response.getInteractionId());
        assertEquals("Hello, world!", response.getCodeOutput());
        assertEquals("print('Hello, world!')", response.getCorrectedCode());
        assertEquals("Your code runs fine.", response.getResponseText());
        assertTrue(response.getHasAudio());

        ArgumentCaptor<CodeAssistInteraction> captor = ArgumentCaptor.forClass(CodeAssistInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertEquals(CodeAssistFeature.DEBUG, captor.getValue().getFeature());
        assertEquals("print(1)", captor.getValue().getCode());
        assertEquals("https://ai.prwatech.com/get_audio/explain.mp3", captor.getValue().getAudioUrl());
    }

    @Test
    void runCodeExecution_noAudioInResponse_hasAudioFalse() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existingUser("user-1")));
        // No courseId set -> courseName defaults to "Python", so this also hits the real-
        // execution path; same graceful-fallback simulation as the test above.
        when(practicalSandboxService.executeAdHoc(anyString()))
                .thenThrow(new IllegalStateException("sandbox not under test here"));
        when(skillamaAiClient.runCodeAssist(
                        any(User.class), anyString(), any(), anyString(), anyString(), isNull(), isNull(), isNull()))
                .thenReturn(generated(null));
        when(interactionRepository.save(any(CodeAssistInteraction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CodeAssistRequestDTO request = new CodeAssistRequestDTO();
        request.setCode("print(1)");

        CodeAssistResponseDTO response = codeAssistService.runCodeExecution("user-1", request);

        assertFalse(response.getHasAudio());
        ArgumentCaptor<CodeAssistInteraction> captor = ArgumentCaptor.forClass(CodeAssistInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertEquals(CodeAssistFeature.CODE_EXECUTION, captor.getValue().getFeature());
    }

    @Test
    void run_missingCode_throwsIllegalArgumentException() {
        CodeAssistRequestDTO request = new CodeAssistRequestDTO();
        assertThrows(IllegalArgumentException.class, () -> codeAssistService.runDebug("user-1", request));
    }

    @Test
    void run_unknownUser_throwsIllegalArgumentException() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        CodeAssistRequestDTO request = new CodeAssistRequestDTO();
        request.setCode("print(1)");
        assertThrows(IllegalArgumentException.class, () -> codeAssistService.runDebug("ghost", request));
    }

    // ---------- getInteractionAudio ----------

    @Test
    void getInteractionAudio_proxiesBytesWithoutExposingRawUrl() {
        CodeAssistInteraction interaction = CodeAssistInteraction.builder()
                .id("interaction-1")
                .userId("user-1")
                .audioUrl("https://ai.prwatech.com/get_audio/explain.mp3")
                .build();
        when(interactionRepository.findById("interaction-1")).thenReturn(Optional.of(interaction));
        when(skillamaAiClient.fetchAudioBytes("https://ai.prwatech.com/get_audio/explain.mp3"))
                .thenReturn(ProxiedAudioDTO.builder().data(new byte[]{1, 2, 3}).contentType("audio/mpeg").build());

        ProxiedAudioDTO audio = codeAssistService.getInteractionAudio("user-1", "interaction-1");

        assertEquals("audio/mpeg", audio.getContentType());
        assertEquals(3, audio.getData().length);
    }

    @Test
    void getInteractionAudio_noAudio_throwsNotFound() {
        CodeAssistInteraction interaction = CodeAssistInteraction.builder()
                .id("interaction-1")
                .userId("user-1")
                .build();
        when(interactionRepository.findById("interaction-1")).thenReturn(Optional.of(interaction));

        assertThrows(NotFoundException.class,
                () -> codeAssistService.getInteractionAudio("user-1", "interaction-1"));
    }

    @Test
    void getInteractionAudio_wrongOwner_throwsNotFound() {
        CodeAssistInteraction interaction = CodeAssistInteraction.builder()
                .id("interaction-1")
                .userId("someone-else")
                .audioUrl("https://ai.prwatech.com/get_audio/explain.mp3")
                .build();
        when(interactionRepository.findById("interaction-1")).thenReturn(Optional.of(interaction));

        assertThrows(NotFoundException.class,
                () -> codeAssistService.getInteractionAudio("user-1", "interaction-1"));
    }

    // ---------- listAdminInteractions ----------

    @Test
    void listAdminInteractions_filtersByFeature() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 10, 0);
        CodeAssistInteraction debugRow = CodeAssistInteraction.builder()
                .id("i-1").userId("user-1").feature(CodeAssistFeature.DEBUG).createdAt(now).build();
        CodeAssistInteraction execRow = CodeAssistInteraction.builder()
                .id("i-2").userId("user-1").feature(CodeAssistFeature.CODE_EXECUTION).createdAt(now).build();
        when(interactionRepository.findAll()).thenReturn(List.of(debugRow, execRow));

        Page<AdminCodeAssistInteractionDTO> page =
                codeAssistService.listAdminInteractions(0, 10, null, null, null, CodeAssistFeature.DEBUG);

        assertEquals(1, page.getTotalElements());
        assertEquals("i-1", page.getContent().get(0).getId());
    }
}
