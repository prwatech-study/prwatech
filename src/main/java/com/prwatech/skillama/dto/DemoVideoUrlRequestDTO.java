package com.prwatech.skillama.dto;

import lombok.Data;

/**
 * Body for {@code PUT /api/admin/platform/demo-video/url}.
 * {@link #videoUrl} may be a YouTube / youtu.be link, an https embed player URL, a direct .mp4 (etc.) URL,
 * or a full {@code <iframe ...>} snippet (the {@code src} is extracted; must be {@code https}).
 */
@Data
public class DemoVideoUrlRequestDTO {
    private String videoUrl;
    private String title;
    private String description;
}
