package com.prwatech.finance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document("user_order")
public class UserOrder {

    @Id
    private String id;

    @Field(name = "user_id")
    private ObjectId userId;

    @Field(name = "course_id")
    private ObjectId courseId;

    @Field(name = "orders_id")
    private ObjectId orders_id;

    //razorpay order_id
    @Field(name = "order_id")
    private String orderId;

    @Field(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
