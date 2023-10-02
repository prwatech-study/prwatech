package com.prwatech.job.repository;

import com.prwatech.job.model.JobsApplied;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobsAppliedRepository extends MongoRepository<JobsApplied, String> {
}
