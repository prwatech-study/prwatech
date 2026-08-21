package com.prwatech.skillama.dto;

import lombok.Data;

@Data
public class TimeWalletAdjustRequestDTO {
    /** Minutes to add (positive) or remove (negative) from the user's time allocation. */
    private Double deltaMinutes;
    private String reason;
}
