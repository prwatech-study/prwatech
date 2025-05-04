package com.prwatech.courses.service;

import com.prwatech.courses.model.Feedback;
import com.prwatech.courses.repository.FeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FeedbackService {
    @Autowired
    private FeedbackRepository feedbackRepository;

    public Feedback saveFeedback(Feedback feedback) {
        feedback.setCreatedAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        return feedbackRepository.save(feedback);
    }

    public Optional<Feedback> getFeedbackById(String id) {
        return feedbackRepository.findById(id);
    }

    public List<Feedback> getFeedbacksByCourseId(String courseId) {
        return feedbackRepository.findByCourseId(courseId);
    }

    public List<Feedback> getFeedbacksByUserId(String userId) {
        return feedbackRepository.findByUserId(userId);
    }

    public Page<Feedback> getAllFeedbacks(int page, int size, String sortBy, boolean desc) {
        Pageable pageable = PageRequest.of(page, size, desc ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return feedbackRepository.findAll(pageable);
    }

    public Feedback updateFeedback(String id, Feedback updated) {
        return feedbackRepository.findById(id).map(existing -> {
            existing.setReview(updated.getReview());
            existing.setRating(updated.getRating());
            existing.setUpdatedAt(LocalDateTime.now());
            return feedbackRepository.save(existing);
        }).orElse(null);
    }

    public void deleteFeedback(String id) {
        feedbackRepository.deleteById(id);
    }
}
