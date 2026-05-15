package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageDeleteResponseDTO {
    private String moduleId;
    private Integer submoduleIndex;
    private String imagePath; // Will be null after deletion
}

