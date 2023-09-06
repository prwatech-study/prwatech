package com.prwatech.coupon.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.persistence.Id;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "coupon")
public class Coupon {

    @Id
    private String id;

    @Field(name = "code")
    private String code;

    @Field(name = "amount_off")
    private Integer amountOff;

    @Field(name = "is_active")
    private Boolean isActive;

    @Field(name = "active_duration")
    private LocalDateTime activeDuration;

    @Field(name = "created_at")
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    private LocalDateTime updatedAt;
}
