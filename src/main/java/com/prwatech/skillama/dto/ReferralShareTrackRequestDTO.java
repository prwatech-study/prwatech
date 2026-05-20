package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class ReferralShareTrackRequestDTO {
    /** WHATSAPP, EMAIL, COPY_LINK, TWITTER, LINKEDIN, OTHER */
    private String channel;
}
