package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.IssueReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IssueReportRepository extends MongoRepository<IssueReport, String> {
    long countByReporterUserId(String reporterUserId);

    Page<IssueReport> findByStatus(String status, Pageable pageable);
}
