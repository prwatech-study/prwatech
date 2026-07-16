package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request to commit a chosen AI-generated image candidate to a submodule's slide. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageCommitRequestDTO {
    private String imageBase64;
    private String contentType;
}
