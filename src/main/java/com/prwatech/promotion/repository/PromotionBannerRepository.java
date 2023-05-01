package com.prwatech.promotion.repository;

import com.prwatech.promotion.model.PromotionBanner;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@EnableMongoRepositories
@Repository
public interface PromotionBannerRepository extends MongoRepository<PromotionBanner, String> {

}
