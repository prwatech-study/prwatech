package com.prwatech.coupon.repository;

import com.prwatech.coupon.model.Coupon;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@EnableMongoRepositories
public interface CouponRepository extends MongoRepository<Coupon, String> {

    Optional<Coupon> findByCode(String code);
}
