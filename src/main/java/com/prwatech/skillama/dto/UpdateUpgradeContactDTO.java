package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class UpdateUpgradeContactDTO {
    private String contactEmail;
    private String contactMessage;
    private String mailtoSubject;
}
