package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeWalletDTO {
    /** True when the user is a time-based (B2B) seat — time replaces the credit wallet. */
    private boolean active;
    private double allocatedMinutes;
    private double consumedMinutes;
    private double remainingMinutes;
}
