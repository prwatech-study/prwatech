package com.prwatech.courses.dto;

import com.prwatech.courses.model.Conversation;
import com.prwatech.courses.model.Forum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ForumDto {

    private String id;
    private String question;
    private List<Conversation> conversation;
    private LocalDateTime lastUpdated;
    private String questionBy;

}
