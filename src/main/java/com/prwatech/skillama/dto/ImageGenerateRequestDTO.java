package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional overrides for AI image generation. When provided (e.g. the admin has
 * unsaved edits in the modal), these are used as the generation source instead of
 * the persisted submodule values. Body is optional; both fields may be null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerateRequestDTO {
    private String label;
    private String scriptText;
}
