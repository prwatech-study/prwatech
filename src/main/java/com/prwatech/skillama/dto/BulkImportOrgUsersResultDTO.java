package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportOrgUsersResultDTO {
    private int createdCount;
    private int managersLinkedCount;
    private List<ImportOrgUserFailureDTO> failures;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportOrgUserFailureDTO {
        private int rowIndex;
        private String email;
        private String message;
    }
}
