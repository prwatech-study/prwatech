package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sales_leads")
public class SalesLead {
    @Id
    private String id;
    private String name;
    private String email;
    private String phone;
    private String message;
    private boolean consentContact;

    @Indexed
    private LeadStatus status;
    private String notes;
    private String contactedByAdminId;
    private LocalDateTime contactedAt;

    @Indexed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum LeadStatus {
        NEW, CONTACTED, CLOSED
    }
}
