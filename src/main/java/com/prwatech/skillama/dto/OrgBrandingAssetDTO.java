package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.OrgAssetKind;
import com.prwatech.skillama.model.OrganizationBranding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgBrandingAssetDTO {
    private OrgAssetKind kind;
    private String url;
    private String s3Bucket;
    private String s3Key;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private OrganizationBranding branding;
}
