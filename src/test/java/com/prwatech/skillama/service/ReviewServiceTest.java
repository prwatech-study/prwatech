package com.prwatech.skillama.service;

import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.dto.AdminReviewReplyRequestDTO;
import com.prwatech.skillama.dto.CreateReviewRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.UserNotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.Review;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserService userService;
    @Mock private EmailServiceImpl emailService;
    @Mock private NotificationSettingsService notificationSettingsService;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, courseRepository, userService,
                emailService, notificationSettingsService);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId("rev1");
            }
            return r;
        });
    }

    private User learner() {
        return User.builder().id("u1").name("Asha").email("asha@x.com").phone("+91999").build();
    }

    private CreateReviewRequestDTO reviewRequest() {
        CreateReviewRequestDTO r = new CreateReviewRequestDTO();
        r.setCourseId("c1");
        r.setRating(5);
        r.setComment("Great course");
        return r;
    }

    @Test
    void saveReviewUnknownUserThrows() {
        when(userService.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.saveReview("ghost", reviewRequest()));
    }

    @Test
    void saveReviewSnapshotsUserAndDefaultsScopeStatusAndCourseName() {
        when(userService.findById("u1")).thenReturn(Optional.of(learner()));
        when(courseRepository.findById("c1")).thenReturn(Optional.of(Course.builder().id("c1").name("Python").build()));

        Review saved = service.saveReview("u1", reviewRequest());

        assertEquals("asha@x.com", saved.getUserEmail());
        assertEquals("Asha", saved.getUserName());
        assertEquals("Python", saved.getCourseName());
        assertEquals(Review.ReviewScope.COURSE, saved.getScope());
        assertEquals(Review.ReviewStatus.OPEN, saved.getStatus());
        assertEquals("Great course", saved.getComment());
        assertEquals("Great course", saved.getReview()); // mirrored
        verify(notificationSettingsService).sendTeamNotification(any(), any(), any());
    }

    @Test
    void saveReviewSucceedsEvenIfNotificationFails() {
        when(userService.findById("u1")).thenReturn(Optional.of(learner()));
        doThrow(new RuntimeException("smtp")).when(notificationSettingsService)
                .sendTeamNotification(any(), any(), any());
        Review saved = service.saveReview("u1", reviewRequest());
        assertEquals("rev1", saved.getId());
    }

    @Test
    void adminReplyRejectsNullBody() {
        assertThrows(IllegalArgumentException.class, () -> service.adminReply("rev1", null, "admin"));
    }

    @Test
    void adminReplyUnknownReviewThrows() {
        when(reviewRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.adminReply("nope", new AdminReviewReplyRequestDTO(), "admin"));
    }

    @Test
    void adminReplyRequiresReplyUnlessClosing() {
        when(reviewRepository.findById("rev1")).thenReturn(Optional.of(openReview()));
        AdminReviewReplyRequestDTO body = new AdminReviewReplyRequestDTO(); // no reply, no status
        assertThrows(IllegalArgumentException.class, () -> service.adminReply("rev1", body, "admin"));
    }

    @Test
    void adminReplyWithTextSetsRepliedStatusAndEmailsUser() {
        Review review = openReview();
        when(reviewRepository.findById("rev1")).thenReturn(Optional.of(review));
        AdminReviewReplyRequestDTO body = new AdminReviewReplyRequestDTO();
        body.setTeamReply("  Thanks for the feedback  ");

        Review saved = service.adminReply("rev1", body, "admin7");

        assertEquals("Thanks for the feedback", saved.getTeamReply()); // trimmed
        assertEquals(Review.ReviewStatus.REPLIED, saved.getStatus());
        assertEquals("admin7", saved.getRepliedBy());
        verify(notificationSettingsService).sendLearnerNotification(any(), eq("asha@x.com"), any(), any());
    }

    @Test
    void adminReplyCloseWithoutTextIsAllowedAndDoesNotEmail() {
        Review review = openReview();
        when(reviewRepository.findById("rev1")).thenReturn(Optional.of(review));
        AdminReviewReplyRequestDTO body = new AdminReviewReplyRequestDTO();
        body.setStatus(Review.ReviewStatus.CLOSED);

        Review saved = service.adminReply("rev1", body, "admin7");

        assertEquals(Review.ReviewStatus.CLOSED, saved.getStatus());
        verify(notificationSettingsService, never()).sendLearnerNotification(any(), any(), any(), any());
    }

    private Review openReview() {
        Review r = new Review();
        r.setId("rev1");
        r.setUserId("u1");
        r.setUserEmail("asha@x.com");
        r.setUserName("Asha");
        r.setComment("Great course");
        r.setStatus(Review.ReviewStatus.OPEN);
        return r;
    }
}
