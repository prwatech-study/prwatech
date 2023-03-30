package com.prwatech.courses.repository;

import com.prwatech.courses.model.CourseDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface CourseDetailRepository extends MongoRepository<CourseDetails, String> {}
