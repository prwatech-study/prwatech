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
@Document(value = "WebinarRegister")
public class WebinarRegister {

    @Id
    private String id;

    @Field(name = "Webinar_Id")
    private ObjectId Webinar_Id;

    @Field(name = "Name")
    private String Name;

    @Field(name = "Email")
    private String Email;

    @Field(name = "Phone_Number")
    private Long Phone_Number;

    @Field(name = "Created_On")
    @CreationTimestamp
    private LocalDateTime Created_On;

}
