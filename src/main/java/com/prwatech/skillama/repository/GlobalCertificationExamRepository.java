package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.CertificationBankBuildStatus;
import com.prwatech.skillama.model.GlobalCertificationExam;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GlobalCertificationExamRepository extends MongoRepository<GlobalCertificationExam, String> {

    List<GlobalCertificationExam> findByActiveTrue();

    List<GlobalCertificationExam> findByProviderIgnoreCaseAndActiveTrue(String provider);

    Optional<GlobalCertificationExam> findByProviderIgnoreCaseAndNameKey(String provider, String nameKey);

    boolean existsByProviderIgnoreCaseAndNameKey(String provider, String nameKey);

    List<GlobalCertificationExam> findByBankStatus(CertificationBankBuildStatus bankStatus);
}
