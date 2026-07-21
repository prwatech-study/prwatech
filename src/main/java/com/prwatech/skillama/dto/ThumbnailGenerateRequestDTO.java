package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Source for AI course-thumbnail generation. When provided (e.g. the admin has
 * unsaved edits on the create/edit form), these override the persisted course
 * values. Body is optional; both fields may be null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbnailGenerateRequestDTO {
    private String name;
    private String description;
}
