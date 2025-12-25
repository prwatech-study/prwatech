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
public class ImageUploadResponseDTO {
    private String imagePath;
    private String imageUrl;
    private String fileName;
    private Long fileSize;
    private String contentType;
    
    // For submodule-specific uploads
    private String moduleId;
    private Integer submoduleIndex;
}

