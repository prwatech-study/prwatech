package com.prwatech.coupon.repository;

import com.prwatech.coupon.model.UsersCoupon;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class UserCouponTemplate {

    private final MongoTemplate mongoTemplate;


    public List<UsersCoupon> getByUserId(ObjectId userId)
    {
        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId));
        return mongoTemplate.find(query, UsersCoupon.class);
    }

    public UsersCoupon getByUserIdAndCouponId(ObjectId userId, ObjectId couponId){
        Query query = new Query();
        query.addCriteria(Criteria.where("user_id").is(userId)
                .andOperator(Criteria.where("coupon_id").is(couponId)));

        return mongoTemplate.findOne(query, UsersCoupon.class);
    }
}
