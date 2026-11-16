package com.prwatech.skillama.repository;

import com.prwatech.skillama.model.outreach.OutreachCampaign;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OutreachCampaignRepository extends MongoRepository<OutreachCampaign, String> {
}