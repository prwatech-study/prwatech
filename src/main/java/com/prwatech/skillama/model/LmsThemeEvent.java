package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "lms_theme_events")
public class LmsThemeEvent {
    @Id
    private String id;

    @Indexed
    private String userId;
    private String userEmail;

    /** Theme switched to: classic | aurora */
    @Indexed
    private String theme;
    private String previousTheme;
    private String source;
    private String pagePath;

    @Indexed
    private LocalDateTime createdAt;
}
