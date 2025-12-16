package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.CourseProgressDTO;
import com.prwatech.skillama.dto.UserCourseDTO;
import com.prwatech.skillama.model.UserCourseEnrollment;

import java.util.List;

public interface UserCourseService {
    List<UserCourseDTO> getUserCoursesWithProgress(String userId);
    CourseProgressDTO getCourseProgress(String userId, String courseId);
    CourseProgressDTO updateProgress(String userId, String courseId, String lectureId, 
                                     boolean completed, Integer timeSpent);
    
    // Enrollment methods
    UserCourseEnrollment enrollUserToCourse(String userId, String courseId, 
                                            UserCourseEnrollment.EnrollmentType enrollmentType);
    void unenrollUserFromCourse(String userId, String courseId);
    boolean isUserEnrolled(String userId, String courseId);
    List<UserCourseEnrollment> getUserEnrollments(String userId);
}

