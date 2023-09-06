package com.prwatech.coupon.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.persistence.Id;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "user_coupon")
public class UsersCoupon {

    @Id
    private String id;

    @Field(name = "user_id")
    private ObjectId userId;

    @Field(name = "coupon_id")
    private ObjectId couponId;

    @Field(name = "is_scratched")
    private Boolean isScratched;

    @Field(name = "is_redeemed")
    private Boolean isRedeemed;

}
