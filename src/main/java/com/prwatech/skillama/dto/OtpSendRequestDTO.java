package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.EmailOtp;
import lombok.Data;

@Data
public class OtpSendRequestDTO {
    private String email;
    /** Optional — validated on freemium signup when provided */
    private String phone;
    private EmailOtp.OtpPurpose purpose;
}
