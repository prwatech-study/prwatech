package com.prwatech.skillama.service;

import com.prwatech.skillama.model.Review;
import com.prwatech.skillama.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;

    public Review saveReview(Review review) {
        return reviewRepository.save(review);
    }

    public Page<Review> getReviews(int page, int size, boolean latestFirst) {
        Sort sort = latestFirst ? Sort.by(Sort.Direction.DESC, "createdAt") : Sort.by(Sort.Direction.ASC, "createdAt");
        return reviewRepository.findAll(PageRequest.of(page, size, sort));
    }
}
