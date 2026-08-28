package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoOtpSendResultDTO {
    /** Where the code went, masked for display (e.g. "j***a@porch.com"). */
    private String maskedEmail;
}
