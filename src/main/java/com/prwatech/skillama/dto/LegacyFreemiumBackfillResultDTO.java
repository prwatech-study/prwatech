package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyFreemiumBackfillResultDTO {
    private boolean dryRun;
    private int legacyUsersFound;
    private int migrated;
    private int skippedStaff;
    private int skippedNoPhone;
    private int skippedInactive;
    @Builder.Default
    private List<String> migratedEmails = new ArrayList<>();
    @Builder.Default
    private List<String> skippedNoPhoneEmails = new ArrayList<>();
    @Builder.Default
    private List<String> skippedStaffEmails = new ArrayList<>();
}
