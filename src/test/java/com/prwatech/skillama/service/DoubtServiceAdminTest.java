package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AdminAiMentorDoubtDTO;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.Doubt;
import com.prwatech.skillama.model.DoubtStatus;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.DoubtRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoubtServiceAdminTest {

    @Mock private DoubtRepository doubtRepository;
    @Mock private SkillamaUserRepository userRepository;
    @Mock private CourseRepository courseRepository;

    @InjectMocks private DoubtService doubtService;

    private Doubt pendingDoubt;
    private Doubt needsMentorDoubt;

    @BeforeEach
    void setUp() {
        pendingDoubt = doubt("d1", "u1", "course-1", DoubtStatus.PENDING,
                LocalDateTime.of(2026, 6, 1, 10, 0));
        needsMentorDoubt = doubt("d2", "u1", "course-2", DoubtStatus.NEEDS_MENTOR,
                LocalDateTime.of(2026, 6, 2, 10, 0));
    }

    @Test
    void listAdminDoubts_filtersByStatus() {
        when(doubtRepository.findAll()).thenReturn(List.of(pendingDoubt, needsMentorDoubt));
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                User.builder().id("u1").name("Ada").email("ada@skillama.co.in").build()));
        when(courseRepository.findById("course-2")).thenReturn(Optional.of(
                Course.builder().id("course-2").name("Pandas Deep Dive").build()));

        Page<AdminAiMentorDoubtDTO> page =
                doubtService.listAdminDoubts(0, 20, null, null, null, DoubtStatus.NEEDS_MENTOR);

        assertEquals(1, page.getTotalElements());
        AdminAiMentorDoubtDTO row = page.getContent().get(0);
        assertEquals("d2", row.getDoubtId());
        assertEquals("Ada", row.getUserName());
        assertEquals("Pandas Deep Dive", row.getCourseName());
        assertEquals("What is a DataFrame?", row.getQuestion());
    }

    @Test
    void listAdminDoubts_sortsNewestFirstAndIncludesLatestAnswer() {
        when(doubtRepository.findAll()).thenReturn(List.of(pendingDoubt, needsMentorDoubt));
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                User.builder().id("u1").name("Ada").email("ada@skillama.co.in").build()));

        Page<AdminAiMentorDoubtDTO> page =
                doubtService.listAdminDoubts(0, 20, null, null, null, null);

        assertEquals(2, page.getTotalElements());
        assertEquals("d2", page.getContent().get(0).getDoubtId());
        assertEquals("A DataFrame is a 2D labeled data structure.", page.getContent().get(0).getLatestAnswer());
    }

    private static Doubt doubt(String id, String userId, String courseId, DoubtStatus status, LocalDateTime ts) {
        return Doubt.builder()
                .id(id)
                .userId(userId)
                .courseId(courseId)
                .status(status)
                .createdAt(ts)
                .updatedAt(ts)
                .messages(List.of(
                        Doubt.DoubtMessage.builder()
                                .id("m1-" + id)
                                .sender(Doubt.Sender.USER)
                                .content("What is a DataFrame?")
                                .timestamp(ts)
                                .build(),
                        Doubt.DoubtMessage.builder()
                                .id("m2-" + id)
                                .sender(Doubt.Sender.AI)
                                .content("A DataFrame is a 2D labeled data structure.")
                                .timestamp(ts)
                                .build()))
                .build();
    }
}
