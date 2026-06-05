package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class LmsThemeSwitchRequestDTO {
    /** classic | aurora */
    private String theme;
    private String previousTheme;
    private String source;
    private String pagePath;
    /** homepage | lms */
    private String context;
    private String visitorId;
}
