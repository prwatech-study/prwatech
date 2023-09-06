package com.prwatech.courses.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Course_Curriculam {

    @JsonProperty(value = "Section_Id")
    private String Section_Id;

    @JsonProperty(value = "Section_Title")
    private String Section_Title;

    @JsonProperty(value = "Section_Description")
    private String Section_Description;

    @JsonProperty(value = "Study_Materials")
    private List<String> Study_Materials;

    @JsonProperty(value = "Topics")
    private List<String> Topics;

    @JsonProperty(value = "Section_Course_Video")
    private String Section_Course_Video;
}
