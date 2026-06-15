package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.UserCourseEnrollment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCourseEnrollmentRepository extends MongoRepository<UserCourseEnrollment, String> {
    List<UserCourseEnrollment> findByUserIdAndStatus(String userId, UserCourseEnrollment.EnrollmentStatus status);
    Optional<UserCourseEnrollment> findByUserIdAndCourseId(String userId, String courseId);
    boolean existsByUserIdAndCourseId(String userId, String courseId);
    long countByUserIdAndStatus(String userId, UserCourseEnrollment.EnrollmentStatus status);
    List<UserCourseEnrollment> findByUserIdAndStatus(String userId, UserCourseEnrollment.EnrollmentStatus status);
    List<UserCourseEnrollment> findByCourseId(String courseId);
    List<UserCourseEnrollment> findByStatus(UserCourseEnrollment.EnrollmentStatus status);
}

