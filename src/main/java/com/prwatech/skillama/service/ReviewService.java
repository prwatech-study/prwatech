package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.AdminReviewReplyRequestDTO;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.dto.CreateReviewRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.UserNotFoundException;
import com.prwatech.skillama.model.Review;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewService.class);
    private final ReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final UserService userService;
    private final NotificationSettingsService notificationSettingsService;

    public Review saveReview(String userId, CreateReviewRequestDTO request) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found or inactive."));

        Review review = new Review();
        review.setUserId(userId);
        review.setUserName(user.getName());
        review.setUserEmail(user.getEmail());
        review.setCourseId(request.getCourseId());
        resolveCourseName(request.getCourseId()).ifPresent(review::setCourseName);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setReview(request.getComment());
        review.setScope(request.getScope() != null ? request.getScope() : Review.ReviewScope.COURSE);
        review.setStatus(Review.ReviewStatus.OPEN);
        review.setCreatedAt(IndiaTime.now());

        Review saved = reviewRepository.save(review);
        sendTeamNotificationEmail(saved, user);
        return saved;
    }

    public Review saveReview(Review review) {
        User user = userService.findById(review.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found or inactive."));
        if (review.getComment() == null && review.getReview() != null) {
            review.setComment(review.getReview());
        }
        if (review.getReview() == null && review.getComment() != null) {
            review.setReview(review.getComment());
        }
        if (review.getScope() == null) {
            review.setScope(Review.ReviewScope.COURSE);
        }
        if (review.getStatus() == null) {
            review.setStatus(Review.ReviewStatus.OPEN);
        }
        if (review.getCreatedAt() == null) {
            review.setCreatedAt(IndiaTime.now());
        }
        if (!StringUtils.hasText(review.getUserEmail())) {
            review.setUserEmail(user.getEmail());
        }
        if (!StringUtils.hasText(review.getUserName())) {
            review.setUserName(user.getName());
        }

        Review saved = reviewRepository.save(review);
        sendTeamNotificationEmail(saved, user);
        return saved;
    }

    public Page<Review> getReviews(String courseId, Review.ReviewScope scope, int page, int size, boolean latestFirst) {
        Sort sort = latestFirst ? Sort.by(Sort.Direction.DESC, "createdAt") : Sort.by(Sort.Direction.ASC, "createdAt");
        PageRequest pageable = PageRequest.of(page, size, sort);
        boolean hasCourse = courseId != null && !courseId.isBlank();
        if (hasCourse && scope != null) {
            return reviewRepository.findByCourseIdAndScope(courseId, scope, pageable);
        }
        if (hasCourse) {
            return reviewRepository.findByCourseId(courseId, pageable);
        }
        if (scope != null) {
            return reviewRepository.findByScope(scope, pageable);
        }
        return reviewRepository.findAll(pageable);
    }

    public Page<Review> getReviewsForUser(String userId, int page, int size) {
        return reviewRepository.findByUserId(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    public Page<Review> getReviewsForAdmin(String courseId, Review.ReviewStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviewsPage;
        boolean hasCourse = courseId != null && !courseId.isBlank();
        if (hasCourse && status != null) {
            reviewsPage = reviewRepository.findByCourseIdAndStatus(courseId, status, pageable);
        } else if (hasCourse) {
            reviewsPage = reviewRepository.findByCourseId(courseId, pageable);
        } else if (status != null) {
            reviewsPage = reviewRepository.findByStatus(status, pageable);
        } else {
            reviewsPage = reviewRepository.findAll(pageable);
        }
        return reviewsPage.map(this::enrichReviewForDisplay);
    }

    public Review adminReply(String reviewId, AdminReviewReplyRequestDTO request, String adminUserId) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));

        boolean closing = request.getStatus() == Review.ReviewStatus.CLOSED;
        boolean hasReply = StringUtils.hasText(request.getTeamReply());

        if (!closing && !hasReply) {
            throw new IllegalArgumentException("teamReply is required unless closing the feedback");
        }

        if (hasReply) {
            review.setTeamReply(request.getTeamReply().trim());
        }
        if (request.getStatus() != null) {
            review.setStatus(request.getStatus());
        } else if (hasReply) {
            review.setStatus(Review.ReviewStatus.REPLIED);
        }

        review.setRepliedAt(IndiaTime.now());
        review.setRepliedBy(adminUserId);

        Review saved = reviewRepository.save(review);
        if (hasReply) {
            sendUserReplyNotificationEmail(saved);
        }
        return enrichReviewForDisplay(saved);
    }

    private Review enrichReviewForDisplay(Review review) {
        if (review == null) {
            return null;
        }
        enrichReviewUserFields(review);
        if (!StringUtils.hasText(review.getCourseName()) && StringUtils.hasText(review.getCourseId())) {
            resolveCourseName(review.getCourseId()).ifPresent(review::setCourseName);
        }
        return review;
    }

    private java.util.Optional<String> resolveCourseName(String courseId) {
        if (!StringUtils.hasText(courseId)) {
            return java.util.Optional.empty();
        }
        return courseRepository.findById(courseId).map(c -> c.getName());
    }

    /**
     * Backfill display fields for legacy reviews created before userName/userEmail were stored.
     */
    private void enrichReviewUserFields(Review review) {
        if (StringUtils.hasText(review.getUserName()) && StringUtils.hasText(review.getUserEmail())) {
            return;
        }
        if (StringUtils.hasText(review.getUserId())) {
            userService.findById(review.getUserId()).ifPresent(user -> {
                if (!StringUtils.hasText(review.getUserName()) && StringUtils.hasText(user.getName())) {
                    review.setUserName(user.getName());
                }
                if (!StringUtils.hasText(review.getUserEmail()) && StringUtils.hasText(user.getEmail())) {
                    review.setUserEmail(user.getEmail());
                }
            });
        }
    }

    private void sendTeamNotificationEmail(Review review, User user) {
        try {
            String subject = "New Skillama feedback — " + review.getRating() + " out of 5";
            String message = "A learner left feedback. Reply from the admin panel and they will get your note by email.\n\n"
                    + "Learner: " + (user.getName() != null ? user.getName() : "—") + "\n"
                    + "Email: " + user.getEmail() + "\n"
                    + "Phone: " + (user.getPhone() != null ? user.getPhone() : "—") + "\n"
                    + "Rating: " + review.getRating() + " out of 5\n"
                    + "Course: " + (review.getCourseName() != null ? review.getCourseName() : "—") + "\n\n"
                    + "What they wrote\n"
                    + review.getComment() + "\n\n"
                    + "— Skillama";

            notificationSettingsService.sendTeamNotification(
                    NotificationEventType.FEEDBACK_NEW, subject, message);
            LOGGER.info("Feedback notification emails sent for review {}", review.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to send feedback notification for review {}", review.getId(), e);
        }
    }

    private void sendUserReplyNotificationEmail(Review review) {
        if (!StringUtils.hasText(review.getUserEmail())) {
            LOGGER.warn("Cannot email user for review {} — no userEmail", review.getId());
            return;
        }
        try {
            String subject = "We replied to your Skillama feedback";
            String message = "Hi"
                    + (StringUtils.hasText(review.getUserName()) ? " " + review.getUserName() : "")
                    + ",\n\n"
                    + "We read your note and replied.\n\n"
                    + "You wrote\n"
                    + review.getComment() + "\n\n"
                    + "Our reply\n"
                    + review.getTeamReply() + "\n\n"
                    + "You can also see this reply in Skillama, under Feedback on your profile.\n\n"
                    + "— Skillama";

            notificationSettingsService.sendLearnerNotification(
                    NotificationEventType.FEEDBACK_REPLY,
                    review.getUserEmail(),
                    subject,
                    message);
            LOGGER.info("Reply notification email sent to {} for review {}", review.getUserEmail(), review.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to send reply notification for review {}", review.getId(), e);
        }
    }
}
