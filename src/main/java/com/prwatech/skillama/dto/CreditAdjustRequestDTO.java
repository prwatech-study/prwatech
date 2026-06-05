package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class CreditAdjustRequestDTO {
    /** Positive increases total allowance; negative reduces it (not below used count) */
    private Integer delta;
    /** Optional: set absolute limit instead of delta on limit */
    private Integer newLimit;
    private String reason;
}
