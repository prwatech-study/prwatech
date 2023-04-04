package com.prwatech.project.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

//Project_Curriculam
//        Array
//        Category_Id
//        5dea2a532d73d1789045f58b
//        Project_Title
//        "Health Care Data Analysis"
//        Project_Description
//        "<p><strong><u>Healthcare Data Analysis </u></strong></p><p><strong><u>…"
//        Actual_Price
//        null
//        Discounted_Price
//        null
//        Enable
//        true
//        __v
//        0
//        Project_Banner
//        "https://api.prwatech.com/uploads/5df9c36afcddaf0ae985dfa4.png"
//        Project_Video
//        "https://api.prwatech.com/uploads/5e40f610ccae757433eca28f.mp4"

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "MiniProject")
public class Project {

    @Id
    private String id;

    @Field(value = "Project_Title")
    private String projectTitle;

    @Field(value = "Project_Description")
    private String projectDescription;

    @Field(value = "Project_Banner")
    private String projectBanner;

    @Field(value = "Project_Video")
    private String projectVideo;



}
