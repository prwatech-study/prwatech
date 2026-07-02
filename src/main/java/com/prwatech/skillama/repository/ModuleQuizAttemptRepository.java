package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.ModuleQuizAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModuleQuizAttemptRepository extends MongoRepository<ModuleQuizAttempt, String> {

    List<ModuleQuizAttempt> findByUserIdAndCourseIdAndModuleNameOrderBySubmittedAtDesc(
            String userId, String courseId, String moduleName);

    List<ModuleQuizAttempt> findByGuestSessionIdAndCourseIdAndModuleNameOrderBySubmittedAtDesc(
            String guestSessionId, String courseId, String moduleName);

    int countByUserIdAndCourseIdAndModuleName(String userId, String courseId, String moduleName);

    int countByGuestSessionIdAndCourseIdAndModuleName(
            String guestSessionId, String courseId, String moduleName);
}
