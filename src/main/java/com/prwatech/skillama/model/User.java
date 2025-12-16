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
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String name;
    private String email;
    private String password;
    private boolean active;
    private String activationKey;
    private GenderEnum gender;
    
    @Indexed
    private UserRole role; // USER, ADMIN, OWNER (defaults to USER)
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy; // User ID who created this user
    private String updatedBy; // User ID who last updated this user
    
    public enum UserRole {
        USER, ADMIN, OWNER
    }
}
