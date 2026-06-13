package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.ChatHistoryItemDTO;
import com.prwatech.skillama.model.UserProfile;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserProfileRepository;
import com.prwatech.skillama.util.IndiaTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceChatHistoryTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseCurriculumRepository curriculumRepository;
    @Mock private CourseService courseService;
    @Mock private FreemiumService freemiumService;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private MongoTemplate skillamaMongoTemplate;
    @Mock private UserCourseService userCourseService;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void getChatHistory_filtersByCourseAndPaginates() {
        UserProfile profile = UserProfile.builder()
                .userId("u1")
                .chatInteractions(new ArrayList<>(List.of(
                        interaction("c1", "Q1", IndiaTime.now().minusMinutes(3)),
                        interaction("c2", "Q2", IndiaTime.now().minusMinutes(2)),
                        interaction("c1", "Q3", IndiaTime.now().minusMinutes(1))
                )))
                .build();

        when(userProfileRepository.findByUserId("u1")).thenReturn(Optional.of(profile));

        List<ChatHistoryItemDTO> page0 = userProfileService.getChatHistory(null, "u1", "c1", 0, 1);
        assertEquals(1, page0.size());
        assertEquals("Q3", page0.get(0).getQuestion());

        List<ChatHistoryItemDTO> page1 = userProfileService.getChatHistory(null, "u1", "c1", 1, 1);
        assertEquals(1, page1.size());
        assertEquals("Q1", page1.get(0).getQuestion());
    }

    @Test
    void getChatHistory_capsPageSizeAt50() {
        when(userProfileRepository.findByUserId("u1")).thenReturn(Optional.empty());

        List<ChatHistoryItemDTO> result = userProfileService.getChatHistory(null, "u1", null, 0, 500);
        assertTrue(result.isEmpty());
    }

    @Test
    void getChatHistory_returnsEmptyWhenNoProfile() {
        when(userProfileRepository.findBySessionId("guest-1")).thenReturn(Optional.empty());

        List<ChatHistoryItemDTO> result = userProfileService.getChatHistory("guest-1", null, "c1", 0, 20);
        assertTrue(result.isEmpty());
    }

    private static UserProfile.ChatInteraction interaction(String courseId, String question, java.time.LocalDateTime ts) {
        return UserProfile.ChatInteraction.builder()
                .id("i-" + question)
                .courseId(courseId)
                .question(question)
                .answer("A")
                .timestamp(ts)
                .build();
    }
}
