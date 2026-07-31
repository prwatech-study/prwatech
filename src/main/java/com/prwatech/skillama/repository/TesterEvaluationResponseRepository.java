package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.TesterEvaluationResponse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TesterEvaluationResponseRepository extends MongoRepository<TesterEvaluationResponse, String> {
}
