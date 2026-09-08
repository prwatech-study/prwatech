package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewOrganizationContractRequestDTO {
    private int termMonths;
    private String packageCode;
    private String poNumber;
    private String notes;
}
