package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.CertificationTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalCertificationExamRequestDTO {
    private String provider;
    private CertificationTier tier;
    private String name;
    private String guidelinesUrl;
    private String description;
    private Boolean active;
}
