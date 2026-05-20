package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.IssueReport;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IssueReportRepository extends MongoRepository<IssueReport, String> {
    long countByReporterUserId(String reporterUserId);
}
