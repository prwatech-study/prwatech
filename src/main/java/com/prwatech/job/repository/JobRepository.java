package com.prwatech.job.repository;

import com.prwatech.job.model.Jobs;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface JobRepository extends MongoRepository<Jobs,String> {
}
