package com.prwatech.skillama.service;

import java.util.List;
import java.util.Map;

/** Default departments an org owner can opt into. Custom codes stay org-private. */
public final class OrgDepartmentCatalog {

    public static final List<Map<String, String>> ITEMS = List.of(
            Map.of("code", "engineering", "name", "Engineering"),
            Map.of("code", "product", "name", "Product"),
            Map.of("code", "sales", "name", "Sales"),
            Map.of("code", "hr", "name", "Human Resources"),
            Map.of("code", "finance", "name", "Finance"),
            Map.of("code", "operations", "name", "Operations"),
            Map.of("code", "it", "name", "IT"),
            Map.of("code", "lnd", "name", "Learning & Development")
    );

    private OrgDepartmentCatalog() {}

    public static Map<String, String> find(String code) {
        if (code == null) return null;
        String needle = code.trim().toLowerCase();
        return ITEMS.stream()
                .filter(item -> needle.equals(item.get("code")))
                .findFirst()
                .orElse(null);
    }
}
