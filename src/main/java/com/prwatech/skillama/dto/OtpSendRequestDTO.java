package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.EmailOtp;
import lombok.Data;

@Data
public class OtpSendRequestDTO {
    private String email;
    private EmailOtp.OtpPurpose purpose;
}
