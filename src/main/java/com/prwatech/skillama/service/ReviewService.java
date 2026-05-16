package com.prwatech.skillama.service;

import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.dto.AdminReviewReplyRequestDTO;
import com.prwatech.skillama.dto.CreateReviewRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.exception.UserNotFoundException;
import com.prwatech.skillama.model.Review;
import com.prwatech.skillama.model.User;
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
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewService.class);
    private static final List<String> TEAM_NOTIFICATION_EMAILS = Arrays.asList(
            "hello@skillama.co.in",
            "jitendrachandwani4@gmail.com"
    );

    private final ReviewRepository reviewRepository;
    private final UserService userService;
    private final EmailServiceImpl emailService;

    public Review saveReview(String userId, CreateReviewRequestDTO request) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found or inactive."));

        Review review = new Review();
        review.setUserId(userId);
        review.setUserName(user.getName());
        review.setUserEmail(user.getEmail());
        review.setCourseId(request.getCourseId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setReview(request.getComment());
        review.setScope(request.getScope() != null ? request.getScope() : Review.ReviewScope.COURSE);
        review.setStatus(Review.ReviewStatus.OPEN);
        review.setCreatedAt(LocalDateTime.now());

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
            review.setCreatedAt(LocalDateTime.now());
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
        return reviewsPage.map(this::enrichReviewUserFields);
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

        review.setRepliedAt(LocalDateTime.now());
        review.setRepliedBy(adminUserId);

        Review saved = reviewRepository.save(review);
        if (hasReply) {
            sendUserReplyNotificationEmail(saved);
        }
        return enrichReviewUserFields(saved);
    }

    /**
     * Backfill display fields for legacy reviews created before userName/userEmail were stored.
     */
    private Review enrichReviewUserFields(Review review) {
        if (review == null) {
            return null;
        }
        if (StringUtils.hasText(review.getUserName()) && StringUtils.hasText(review.getUserEmail())) {
            return review;
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
        return review;
    }

    private void sendTeamNotificationEmail(Review review, User user) {
        try {
            String subject = "New Skillama feedback — " + review.getRating() + "★";
            String message = "A user submitted feedback on Skillama.\n\n"
                    + "User: " + (user.getName() != null ? user.getName() : "N/A") + "\n"
                    + "Email: " + user.getEmail() + "\n"
                    + "Phone: " + (user.getPhone() != null ? user.getPhone() : "N/A") + "\n"
                    + "Course ID: " + (review.getCourseId() != null ? review.getCourseId() : "N/A") + "\n"
                    + "Scope: " + (review.getScope() != null ? review.getScope() : "COURSE") + "\n"
                    + "Rating: " + review.getRating() + "/5\n"
                    + "Status: " + review.getStatus() + "\n"
                    + "Review ID: " + review.getId() + "\n\n"
                    + "Feedback:\n" + review.getComment() + "\n\n"
                    + "Reply from the admin panel to respond to the user.";

            for (String teamEmail : TEAM_NOTIFICATION_EMAILS) {
                emailService.sendEmail(new EmailSendDto(teamEmail, subject, message));
            }
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
            String subject = "Skillama team replied to your feedback";
            String message = "Hello"
                    + (StringUtils.hasText(review.getUserName()) ? " " + review.getUserName() : "")
                    + ",\n\n"
                    + "Our team has responded to your feedback"
                    + (review.getCourseId() != null ? " (course: " + review.getCourseId() + ")" : "")
                    + ".\n\n"
                    + "Your message:\n" + review.getComment() + "\n\n"
                    + "Team reply:\n" + review.getTeamReply() + "\n\n"
                    + "You can also view this reply when you sign in to Skillama LMS "
                    + "(Feedback panel on your profile or in the learning area).\n\n"
                    + "Thank you,\nSkillama Team";

            emailService.sendEmail(new EmailSendDto(review.getUserEmail(), subject, message));
            LOGGER.info("Reply notification email sent to {} for review {}", review.getUserEmail(), review.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to send reply notification for review {}", review.getId(), e);
        }
    }
}
