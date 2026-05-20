package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.ReferralShareEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReferralShareEventRepository extends MongoRepository<ReferralShareEvent, String> {
}
