package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationRequestDTO {
    private String name;
    private String slug;
    private String contactEmail;
    private String salesContactEmail;
    private List<String> allowedEmailDomains;
    private String packageCode;
    private int termMonths;
    private LocalDateTime contractStart;
    private String poNumber;
    private RootOwnerDTO rootOwner;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RootOwnerDTO {
        private String name;
        private String email;
        private String password;
    }
}
