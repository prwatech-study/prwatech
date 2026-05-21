package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class CreditAdjustRequestDTO {
    /** Positive adds headroom (reduces used), negative consumes extra allowance */
    private Integer delta;
    /** Optional: set absolute limit instead of delta on limit */
    private Integer newLimit;
    private String reason;
}
