package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgAuthGoogleRequestDTO {
    private String orgSlug;
    private String idToken;
    private boolean forceLogin;
}
