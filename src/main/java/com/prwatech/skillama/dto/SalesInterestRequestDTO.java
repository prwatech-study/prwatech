package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class SalesInterestRequestDTO {
    private String name;
    private String email;
    private String phone;
    private String message;
    private Boolean consentContact;
}
