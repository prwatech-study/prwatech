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
public class QueryCreditRepairResultDTO {
    private boolean dryRun;
    private int usersScanned;
    private int usersRepaired;
    private int usersAlreadyCorrect;
    private int usersSkippedUnlimited;

    @Builder.Default
    private List<UserRepairDetail> repairs = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRepairDetail {
        private String userId;
        private String email;
        private int usedBefore;
        private int usedAfter;
        private int limitBefore;
        private int limitAfter;
        private long activityLogCount;
        private String note;
    }
}
