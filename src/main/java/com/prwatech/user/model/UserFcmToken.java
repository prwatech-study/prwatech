package com.prwatech.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.persistence.Id;
import java.time.LocalDateTime;


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "user_fcm")
public class UserFcmToken {

    @Id
    private String id;

    @Field(name = "user_id")
    private ObjectId userId;

    @Field(name = "fcm_token")
    private String fcmToken;

    @Field(name = "status")
    private Integer status;

    @Field(name = "message")
    private String message;

    @CreationTimestamp
    @Field(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Field(name = "updated_at")
    private LocalDateTime updatedAt;
}
