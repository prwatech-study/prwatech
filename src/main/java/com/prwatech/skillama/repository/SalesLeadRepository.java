package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.SalesLead;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SalesLeadRepository extends MongoRepository<SalesLead, String> {
}
