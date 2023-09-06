package com.prwatech.courses.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "Webinar")
public class Webinar {

    @Id
    private String id;

    @Field(name = "Webinar_Title")
    private String Webinar_Title;

    @Field(name = "Webinar_Description")
    private String Webinar_Description;

    @Field(name = "Webinar_Date_Time")
    private LocalDateTime Webinar_Date_Time;

    @Field(name = "Webinar_Type")
    private String Webinar_Type;

    @Field(name = "Webinar_Link")
    private String Webinar_Link;

    @CreationTimestamp
    @Field(name = "Created_On")
    private LocalDateTime Created_On;
}
