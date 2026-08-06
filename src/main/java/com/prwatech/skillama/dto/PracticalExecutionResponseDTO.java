package com.prwatech.skillama.dto;

import lombok.*;

import java.util.List;

/** {@code status}: ok | rejected | error. {@code violations} populated only when rejected. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticalExecutionResponseDTO {
    private String status;
    private String stdout;
    private String result;
    private List<String> figures;
    private List<String> violations;
    private String error;
}
