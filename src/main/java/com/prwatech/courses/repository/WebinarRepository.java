package com.prwatech.courses.repository;

import com.prwatech.courses.model.Webinar;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface WebinarRepository extends MongoRepository<Webinar, String> {
}
