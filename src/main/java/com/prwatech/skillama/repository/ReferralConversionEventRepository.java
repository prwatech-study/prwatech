package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.ReferralConversionEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReferralConversionEventRepository extends MongoRepository<ReferralConversionEvent, String> {
    long countByReferrerId(String referrerId);

    List<ReferralConversionEvent> findByReferrerIdOrderByCreatedAtDesc(String referrerId);
}
