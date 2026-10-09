package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CertificationBankQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CertificationBankQuestionRepository extends MongoRepository<CertificationBankQuestion, String> {

    List<CertificationBankQuestion> findByCertificationExamIdAndBankVersionAndActiveTrueOrderByCreatedAtAsc(
            String certificationExamId, int bankVersion);

    long countByCertificationExamIdAndBankVersionAndActiveTrue(String certificationExamId, int bankVersion);

    void deleteByCertificationExamIdAndBankVersionLessThan(String certificationExamId, int bankVersion);

    void deleteByCertificationExamIdAndBankVersion(String certificationExamId, int bankVersion);

    void deleteByCertificationExamId(String certificationExamId);
}
