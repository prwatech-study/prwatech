package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResponseDTO {
    private String submoduleId;
    private String imagePath;
    private String imageUrl;
    private String fileName;
    private Long fileSize;
    private String contentType;
}
