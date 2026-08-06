package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.PracticalExecutionLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PracticalExecutionLogRepository extends MongoRepository<PracticalExecutionLog, String> {
}
