package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpgradeContactDTO {
    private String contactEmail;
    private String contactMessage;
    private String mailtoSubject;
}
