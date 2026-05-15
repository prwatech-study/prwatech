package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
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
    private LocalDateTime createdAt;
}
