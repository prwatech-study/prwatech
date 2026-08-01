package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.SalesLead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SalesLeadRepository extends MongoRepository<SalesLead, String> {
    Page<SalesLead> findByStatusOrderByCreatedAtDesc(SalesLead.LeadStatus status, Pageable pageable);
}
