package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.dto.AdminChatInteractionDTO;
import com.prwatech.skillama.dto.ProxiedAudioDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceAdminChatTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseCurriculumRepository curriculumRepository;
    @Mock private CourseService courseService;
    @Mock private FreemiumService freemiumService;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private MongoTemplate skillamaMongoTemplate;
    @Mock private UserCourseService userCourseService;
    @Mock private SkillamaAiClient skillamaAiClient;

    @InjectMocks private UserProfileService userProfileService;

    private UserProfile profile;

    @BeforeEach
    void setUp() {
        profile = UserProfile.builder()
                .userId("u1")
                .isGuest(false)
                .chatInteractions(List.of(
                        chat("c1", "What is Python?", "A language", LocalDateTime.of(2026, 6, 1, 10, 0)),
                        chat("c2", "What is Java?", "Another language", LocalDateTime.of(2026, 6, 2, 10, 0))))
                .build();
    }

    @Test
    void listAdminChatInteractions_filtersByCourseAndIncludesUserDetails() {
        when(userProfileRepository.findAll()).thenReturn(List.of(profile));
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                User.builder().id("u1").name("Ada").email("ada@skillama.co.in").build()));
        when(courseRepository.findById("c1")).thenReturn(Optional.of(
                Course.builder().id("c1").name("Python Basics").build()));

        Page<AdminChatInteractionDTO> page =
                userProfileService.listAdminChatInteractions(0, 20, null, "c1", null);

        assertEquals(1, page.getTotalElements());
        AdminChatInteractionDTO row = page.getContent().get(0);
        assertEquals("Ada", row.getUserName());
        assertEquals("ada@skillama.co.in", row.getUserEmail());
        assertEquals("Python Basics", row.getCourseName());
        assertEquals("What is Python?", row.getQuestion());
    }

    @Test
    void listAdminChatInteractions_sortsNewestFirst() {
        when(userProfileRepository.findAll()).thenReturn(List.of(profile));
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                User.builder().id("u1").name("Ada").email("ada@skillama.co.in").build()));

        Page<AdminChatInteractionDTO> page =
                userProfileService.listAdminChatInteractions(0, 20, null, null, null);

        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().get(0).getQuestion().contains("Java"));
    }

    @Test
    void listAdminChatInteractions_neverExposesRawAudioUrlOnlyHasAudioFlag() {
        when(userProfileRepository.findAll()).thenReturn(List.of(profile));
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                User.builder().id("u1").name("Ada").email("ada@skillama.co.in").build()));

        Page<AdminChatInteractionDTO> page =
                userProfileService.listAdminChatInteractions(0, 20, null, "c1", null);

        // c1's interaction was built without an audioUrl.
        assertFalse(page.getContent().get(0).getHasAudio());
    }

    @Test
    void getChatInteractionAudio_proxiesBytesWithoutExposingRawUrl() {
        UserProfile withAudio = UserProfile.builder()
                .userId("u1")
                .isGuest(false)
                .chatInteractions(List.of(
                        UserProfile.ChatInteraction.builder()
                                .id("chat-1").courseId("c1")
                                .question("What is Python?").answer("A language")
                                .audioUrl("https://ai.prwatech.com/get_audio/answer.mp3")
                                .timestamp(LocalDateTime.of(2026, 6, 1, 10, 0))
                                .build()))
                .build();
        when(userProfileRepository.findByUserId("u1")).thenReturn(Optional.of(withAudio));
        when(skillamaAiClient.fetchAudioBytes("https://ai.prwatech.com/get_audio/answer.mp3"))
                .thenReturn(ProxiedAudioDTO.builder().data(new byte[]{1, 2, 3}).contentType("audio/mpeg").build());

        ProxiedAudioDTO audio = userProfileService.getChatInteractionAudio(null, "u1", "chat-1");

        assertEquals("audio/mpeg", audio.getContentType());
        assertEquals(3, audio.getData().length);
    }

    @Test
    void getChatInteractionAudio_throwsWhenInteractionHasNoAudio() {
        when(userProfileRepository.findByUserId("u1")).thenReturn(Optional.of(profile));

        assertThrows(NotFoundException.class,
                () -> userProfileService.getChatInteractionAudio(null, "u1", "chat-c1-" + "What is Python?".hashCode()));
    }

    @Test
    void getChatInteractionAudio_throwsWhenProfileNotFound() {
        when(userProfileRepository.findByUserId("someone-else")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> userProfileService.getChatInteractionAudio(null, "someone-else", "chat-1"));
    }

    private static UserProfile.ChatInteraction chat(
            String courseId, String question, String answer, LocalDateTime ts) {
        return UserProfile.ChatInteraction.builder()
                .id("chat-" + courseId + "-" + question.hashCode())
                .courseId(courseId)
                .question(question)
                .answer(answer)
                .questionType("text")
                .timestamp(ts)
                .build();
    }
}
