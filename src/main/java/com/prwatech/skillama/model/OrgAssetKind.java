package com.prwatech.skillama.model;

public enum OrgAssetKind {
    LOGO,
    FAVICON,
    LOGIN_BACKGROUND,
    CERTIFICATE_LOGO;

    public String objectBaseName() {
        return name().toLowerCase().replace('_', '-');
    }
}
