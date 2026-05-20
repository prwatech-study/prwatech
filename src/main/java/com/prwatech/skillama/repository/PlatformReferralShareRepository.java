package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.PlatformReferralShare;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlatformReferralShareRepository extends MongoRepository<PlatformReferralShare, String> {
}
