package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class OutreachCampaignUpdateDTO {
    /** RUNNING, PAUSED, DRAFT */
    private String status;
    private Integer dailyBatchSize;
    /** Master admin toggle for the legacy outreach feature. */
    private Boolean outreachEnabled;
}