package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class AppleAuthRequestDTO {
    private String identityToken;
    /** Apple only sends name on first authorization. */
    private String name;
}
