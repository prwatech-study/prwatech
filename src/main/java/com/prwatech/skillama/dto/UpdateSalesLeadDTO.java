package com.prwatech.skillama.dto;

import com.prwatech.skillama.model.SalesLead;
import lombok.Data;

@Data
public class UpdateSalesLeadDTO {
    private SalesLead.LeadStatus status;
    private String notes;
}
