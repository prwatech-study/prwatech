package com.prwatech.courses.model;

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

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "course_track")
public class CourseTrack {

    @Id
    private String id;

    @Field(name = "user_id")
    private ObjectId userId;

    @Field(name = "course_id")
    private ObjectId courseId;

    @Field(name = "is_all_completed")
    private Boolean isAllCompleted=Boolean.FALSE;

    @Field(name = "total_size")
    private Integer totalSize;

    @Field(name = "current_item")
    private Integer currentItem=1;

    @Field(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Field(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
