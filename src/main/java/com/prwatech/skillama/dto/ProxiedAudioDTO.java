package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw audio bytes fetched server-side from the ai-tutor service, for endpoints that
 * proxy playback instead of ever handing the client the underlying (unauthenticated,
 * permanent) external URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxiedAudioDTO {
    private byte[] data;
    private String contentType;
}
