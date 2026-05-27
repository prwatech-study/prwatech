package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactAvailabilityDTO {
    private boolean emailAvailable;
    private boolean phoneAvailable;
    private boolean comboAvailable;
    private String message;
}
