package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.UpgradeRequest;
import lombok.Data;

@Data
public class UpdateUpgradeRequestDTO {
    private UpgradeRequest.RequestStatus status;
    private String notes;
}
