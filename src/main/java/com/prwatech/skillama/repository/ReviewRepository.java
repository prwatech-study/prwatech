package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReviewRepository extends MongoRepository<Review, String> {
    Page<Review> findByCourseId(String courseId, Pageable pageable);

    Page<Review> findByScope(Review.ReviewScope scope, Pageable pageable);

    Page<Review> findByCourseIdAndScope(String courseId, Review.ReviewScope scope, Pageable pageable);

    Page<Review> findByUserId(String userId, Pageable pageable);

    Page<Review> findByStatus(Review.ReviewStatus status, Pageable pageable);

    Page<Review> findByCourseIdAndStatus(String courseId, Review.ReviewStatus status, Pageable pageable);
}
