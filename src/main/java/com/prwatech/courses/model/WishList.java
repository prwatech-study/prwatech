package com.prwatech.courses.model;

import com.prwatech.courses.enums.CourseLevelCategory;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
@NoArgsConstructor
@Document("wish_list")
public class WishList {

    @Id
    private String id;

    @Field(name = "user_id")
    private ObjectId userId;

    @Field(name = "course_id")
    private ObjectId courseId;

    @Field(name = "course_type")
    private CourseLevelCategory courseType;

    @CreationTimestamp
    @Field(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Field(name = "updated_at")
    private LocalDateTime updatedAt;
}
