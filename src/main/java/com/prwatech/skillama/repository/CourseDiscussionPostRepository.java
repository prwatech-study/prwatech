package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CourseDiscussionPost;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CourseDiscussionPostRepository extends MongoRepository<CourseDiscussionPost, String> {
    List<CourseDiscussionPost> findByCourseIdAndActiveTrueOrderByCreatedAtDesc(String courseId);
}
