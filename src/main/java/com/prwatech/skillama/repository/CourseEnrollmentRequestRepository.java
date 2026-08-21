package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CourseEnrollmentRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentRequestRepository extends MongoRepository<CourseEnrollmentRequest, String> {
    List<CourseEnrollmentRequest> findByUserIdOrderByCreatedAtDesc(String userId);

    List<CourseEnrollmentRequest> findByStatusOrderByCreatedAtDesc(CourseEnrollmentRequest.RequestStatus status);

    List<CourseEnrollmentRequest> findAllByOrderByCreatedAtDesc();

    Optional<CourseEnrollmentRequest> findFirstByUserIdAndCourseIdAndStatus(
            String userId, String courseId, CourseEnrollmentRequest.RequestStatus status);

    long countByStatus(CourseEnrollmentRequest.RequestStatus status);
}
