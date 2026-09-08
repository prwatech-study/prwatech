package com.prwatech.skillama.model;

/**
 * Org operator modules. ORG_OWNER always has full access.
 * Null/empty {@code orgModulePermissions} on an ORG_ADMIN = legacy full access.
 */
public enum OrgModule {
    USERS,
    ASSIGNMENTS,
    REPORTING,
    BRANDING,
    SECURITY,
    DEPARTMENTS
}
