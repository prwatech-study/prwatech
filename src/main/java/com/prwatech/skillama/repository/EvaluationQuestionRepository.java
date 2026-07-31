package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.EvaluationQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationQuestionRepository extends MongoRepository<EvaluationQuestion, String> {
    List<EvaluationQuestion> findByActiveTrueOrderByCategoryAscOrderAsc();

    List<EvaluationQuestion> findAllByOrderByCategoryAscOrderAsc();
}
