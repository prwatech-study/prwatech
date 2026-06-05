package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.DemoVideoDTO;
import com.prwatech.skillama.model.PlatformDemoVideo;
import com.prwatech.skillama.repository.PlatformDemoVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlatformDemoVideoService {

    private static final String DEMO_VIDEO_S3_PREFIX = "demo-video";
    private static final String PLAYBACK_DIRECT = "direct";
    private static final String PLAYBACK_YOUTUBE = "youtube";
    private static final String PLAYBACK_EMBED = "embed";

    private static final Pattern IFRAME_SRC =
            Pattern.compile("src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final PlatformDemoVideoRepository repository;
    private final FileStorageService fileStorageService;

    @Value("${file.upload.s3.demo-video-prefix:demo-video}")
    private String demoVideoPrefix;

    public DemoVideoDTO getPublicConfig() {
        return repository.findById(PlatformDemoVideo.SINGLETON_ID)
                .map(this::toDto)
                .orElse(DemoVideoDTO.builder().available(false).build());
    }

    public DemoVideoDTO upload(MultipartFile video, String title, String description, String adminUserId)
            throws IOException {
        String prefix = StringUtils.hasText(demoVideoPrefix) ? demoVideoPrefix : DEMO_VIDEO_S3_PREFIX;
        String videoUrl = fileStorageService.uploadVideoToS3(video, prefix);
        return saveConfig(
                videoUrl,
                video.getContentType(),
                video.getSize(),
                video.getOriginalFilename(),
                title,
                description,
                adminUserId,
                PLAYBACK_DIRECT);
    }

    /**
     * Saves demo video from a URL or iframe snippet. Supports:
     * <ul>
     *   <li>YouTube watch, embed, Shorts, or youtu.be links — stored as embed URL, {@code playbackType=youtube}</li>
     *   <li>Direct HTTPS links to .mp4 / .webm / .mov / .avi — {@code playbackType=direct}</li>
     *   <li>Other HTTPS URLs (Vimeo, Loom, etc.) — {@code playbackType=embed} for iframe playback</li>
     *   <li>Raw {@code &lt;iframe ... src="..."&gt;} HTML — extracts {@code src} (must be https)</li>
     * </ul>
     */
    public DemoVideoDTO saveFromUrl(String videoUrlOrIframe, String title, String description, String adminUserId) {
        if (!StringUtils.hasText(videoUrlOrIframe)) {
            throw new IllegalArgumentException("A video URL or iframe snippet is required");
        }
        ParsedExternal parsed = parseExternalVideoInput(videoUrlOrIframe.trim());
        return saveConfig(
                parsed.canonicalUrl(),
                null,
                null,
                null,
                title,
                description,
                adminUserId,
                parsed.playbackType());
    }

    public DemoVideoDTO updateMetadata(String title, String description, String adminUserId) {
        PlatformDemoVideo config = repository.findById(PlatformDemoVideo.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("No demo video configured yet"));
        if (StringUtils.hasText(title)) {
            config.setTitle(title.trim());
        }
        if (description != null) {
            config.setDescription(description.trim());
        }
        config.setUpdatedAt(IndiaTime.now());
        config.setUpdatedBy(adminUserId);
        return toDto(repository.save(config));
    }

    public void remove(String adminUserId) {
        repository.findById(PlatformDemoVideo.SINGLETON_ID).ifPresent(config -> {
            if (StringUtils.hasText(config.getVideoUrl()) && fileStorageService.isManagedStorageUrl(config.getVideoUrl())) {
                try {
                    fileStorageService.deleteFile(config.getVideoUrl());
                } catch (IOException e) {
                    // Best-effort delete from S3; still remove DB record
                }
            }
            repository.delete(config);
        });
    }

    private DemoVideoDTO saveConfig(
            String videoUrl,
            String contentType,
            Long fileSizeBytes,
            String originalFileName,
            String title,
            String description,
            String adminUserId,
            String playbackType) {
        repository.findById(PlatformDemoVideo.SINGLETON_ID).ifPresent(existing -> {
            if (StringUtils.hasText(existing.getVideoUrl())
                    && !existing.getVideoUrl().equals(videoUrl)
                    && fileStorageService.isManagedStorageUrl(existing.getVideoUrl())) {
                try {
                    fileStorageService.deleteFile(existing.getVideoUrl());
                } catch (IOException ignored) {
                    // continue
                }
            }
        });

        PlatformDemoVideo config = repository.findById(PlatformDemoVideo.SINGLETON_ID)
                .orElse(new PlatformDemoVideo());

        config.setId(PlatformDemoVideo.SINGLETON_ID);
        config.setTitle(StringUtils.hasText(title) ? title.trim() : "How to use Skillama");
        config.setDescription(description != null ? description.trim() : null);
        config.setVideoUrl(videoUrl);
        config.setPlaybackType(playbackType);
        config.setS3Key(null);
        config.setContentType(contentType);
        config.setFileSizeBytes(fileSizeBytes);
        config.setOriginalFileName(originalFileName);
        config.setEnabled(true);
        config.setUpdatedAt(IndiaTime.now());
        config.setUpdatedBy(adminUserId);

        return toDto(repository.save(config));
    }

    private DemoVideoDTO toDto(PlatformDemoVideo config) {
        boolean available = config.isEnabled() && StringUtils.hasText(config.getVideoUrl());
        String playback =
                StringUtils.hasText(config.getPlaybackType()) ? config.getPlaybackType() : PLAYBACK_DIRECT;
        return DemoVideoDTO.builder()
                .available(available)
                .title(config.getTitle())
                .description(config.getDescription())
                .playbackType(available ? playback : null)
                .videoUrl(available ? config.getVideoUrl() : null)
                .contentType(config.getContentType())
                .fileSizeBytes(config.getFileSizeBytes())
                .originalFileName(config.getOriginalFileName())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private record ParsedExternal(String playbackType, String canonicalUrl) {}

    private static ParsedExternal parseExternalVideoInput(String raw) {
        String working = raw.trim();
        if (working.toLowerCase(Locale.ROOT).contains("<iframe")) {
            String extracted = extractIframeSrc(working);
            if (!StringUtils.hasText(extracted)) {
                throw new IllegalArgumentException("Could not find a src URL in the iframe HTML");
            }
            working = extracted.trim();
        }

        URI uri;
        try {
            uri = URI.create(working);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL");
        }
        if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only https URLs are allowed");
        }

        String youtubeId = extractYoutubeVideoId(working);
        if (youtubeId != null) {
            if (!youtubeId.matches("[a-zA-Z0-9_-]{6,64}")) {
                throw new IllegalArgumentException("Invalid YouTube video id");
            }
            return new ParsedExternal(PLAYBACK_YOUTUBE, "https://www.youtube.com/embed/" + youtubeId);
        }

        if (looksLikeDirectVideoFileUrl(working) || looksLikeS3VideoUrl(working)) {
            return new ParsedExternal(PLAYBACK_DIRECT, working);
        }

        return new ParsedExternal(PLAYBACK_EMBED, working);
    }

    private static String extractIframeSrc(String iframeHtml) {
        Matcher m = IFRAME_SRC.matcher(iframeHtml);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Returns the YouTube video id if the URL is a known YouTube / youtu.be shape; otherwise null.
     */
    private static String extractYoutubeVideoId(String url) {
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("youtu.be")) {
                String p = u.getPath();
                if (p != null && p.length() > 1) {
                    return firstPathSegment(p.substring(1));
                }
            }
            if (host.equals("www.youtube.com")
                    || host.equals("youtube.com")
                    || host.equals("m.youtube.com")
                    || host.equals("www.youtube-nocookie.com")) {
                String path = u.getPath();
                if (path != null) {
                    if (path.startsWith("/embed/")) {
                        return firstPathSegment(path.substring("/embed/".length()));
                    }
                    if (path.startsWith("/shorts/")) {
                        return firstPathSegment(path.substring("/shorts/".length()));
                    }
                    if (path.startsWith("/live/")) {
                        return firstPathSegment(path.substring("/live/".length()));
                    }
                }
                String q = u.getQuery();
                if (q != null) {
                    for (String param : q.split("&")) {
                        if (param.startsWith("v=")) {
                            return param.substring(2).split("&")[0];
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static String firstPathSegment(String pathRemainder) {
        if (!StringUtils.hasText(pathRemainder)) {
            return null;
        }
        int slash = pathRemainder.indexOf('/');
        return slash < 0 ? pathRemainder : pathRemainder.substring(0, slash);
    }

    private static boolean looksLikeS3VideoUrl(String url) {
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.contains("amazonaws.com") || host.contains("s3.");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean looksLikeDirectVideoFileUrl(String url) {
        String path;
        try {
            path = URI.create(url.trim()).getPath();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4")
                || lower.endsWith(".webm")
                || lower.endsWith(".mov")
                || lower.endsWith(".avi")
                || lower.endsWith(".m4v");
    }
}
