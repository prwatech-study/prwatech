package com.prwatech.coupon.repository;

import com.prwatech.coupon.model.UsersCoupon;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

@Repository
@EnableMongoRepositories
public interface UserCouponRepository extends MongoRepository<UsersCoupon, String> {
}
