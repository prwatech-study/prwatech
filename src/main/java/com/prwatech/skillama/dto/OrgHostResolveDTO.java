package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgHostResolveDTO {
    private String organizationId;
    private String slug;
    private String name;
    private boolean customDomain;
}
