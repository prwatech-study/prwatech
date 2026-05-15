package com.prwatech.skillama.controller;

import com.prwatech.authentication.security.JwtUtils;
import com.prwatech.skillama.dto.CreateReviewRequestDTO;
import com.prwatech.skillama.model.Review;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.ReviewService;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/skillama/review")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    /**
     * Backward compatible: accepts legacy body with userId in Review.
     * New clients may omit userId and send Authorization Bearer instead.
     */
    @PostMapping
    public ResponseEntity<Review> createReview(
            @RequestBody Review review,
            HttpServletRequest httpRequest) {
        if (review.getUserId() == null || review.getUserId().isBlank()) {
            String userId = extractUserIdOptional(httpRequest);
            if (userId != null) {
                review.setUserId(userId);
            } else {
                throw new RuntimeException("userId required in body or Authorization header");
            }
        }
        Review saved = reviewService.saveReview(review);
        return ResponseEntity.ok(saved);
    }

    /**
     * New shape with courseId, comment, scope (optional; legacy POST above still works).
     */
    @PostMapping("/v2")
    public ResponseEntity<Review> createReviewV2(
            @RequestBody CreateReviewRequestDTO request,
            HttpServletRequest httpRequest) {
        String userId = extractUserIdOptional(httpRequest);
        if (userId == null) {
            throw new RuntimeException("Authorization required");
        }
        return ResponseEntity.ok(reviewService.saveReview(userId, request));
    }

    @GetMapping
    public ResponseEntity<Page<Review>> getReviews(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) Review.ReviewScope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "true") boolean latestFirst) {
        Page<Review> reviews = reviewService.getReviews(courseId, scope, page, size, latestFirst);
        return ResponseEntity.ok(reviews);
    }

    /** Logged-in user's own feedback submissions (includes team replies). */
    @GetMapping("/mine")
    public ResponseEntity<Page<Review>> getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        String userId = extractUserIdOptional(httpRequest);
        if (userId == null) {
            throw new RuntimeException("Authorization required");
        }
        return ResponseEntity.ok(reviewService.getReviewsForUser(userId, page, size));
    }

    private String extractUserIdOptional(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String email = jwtUtils.extractUsername(authHeader.substring(7));
            return userService.findByEmail(email).map(User::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
