package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.DemoVideoDTO;
import com.prwatech.skillama.model.PlatformDemoVideo;
import com.prwatech.skillama.repository.PlatformDemoVideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformDemoVideoServiceTest {

    @Mock private PlatformDemoVideoRepository repository;
    @Mock private FileStorageService fileStorageService;

    private PlatformDemoVideoService service;

    @BeforeEach
    void setUp() {
        service = new PlatformDemoVideoService(repository, fileStorageService);
        when(repository.findById(PlatformDemoVideo.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(any(PlatformDemoVideo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void publicConfigUnavailableWhenNoneConfigured() {
        DemoVideoDTO dto = service.getPublicConfig();
        assertFalse(dto.isAvailable());
    }

    @Test
    void saveFromUrlRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> service.saveFromUrl("  ", "t", "d", "admin"));
    }

    @Test
    void saveFromUrlRejectsNonHttps() {
        assertThrows(IllegalArgumentException.class,
                () -> service.saveFromUrl("http://insecure.com/v.mp4", "t", "d", "admin"));
    }

    @Test
    void youtubeWatchUrlBecomesEmbed() {
        DemoVideoDTO dto = service.saveFromUrl(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "t", "d", "admin");
        assertEquals("youtube", dto.getPlaybackType());
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", dto.getVideoUrl());
    }

    @Test
    void youtuBeShortUrlBecomesEmbed() {
        DemoVideoDTO dto = service.saveFromUrl("https://youtu.be/dQw4w9WgXcQ", "t", "d", "admin");
        assertEquals("youtube", dto.getPlaybackType());
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", dto.getVideoUrl());
    }

    @Test
    void directMp4IsDirectPlayback() {
        DemoVideoDTO dto = service.saveFromUrl("https://cdn.example.com/demo.mp4", "t", "d", "admin");
        assertEquals("direct", dto.getPlaybackType());
        assertEquals("https://cdn.example.com/demo.mp4", dto.getVideoUrl());
    }

    @Test
    void otherHttpsUrlIsEmbed() {
        DemoVideoDTO dto = service.saveFromUrl("https://vimeo.com/123456", "t", "d", "admin");
        assertEquals("embed", dto.getPlaybackType());
    }

    @Test
    void iframeSnippetSrcIsExtracted() {
        DemoVideoDTO dto = service.saveFromUrl(
                "<iframe src=\"https://player.vimeo.com/video/999\" allow=\"fullscreen\"></iframe>",
                "t", "d", "admin");
        assertEquals("embed", dto.getPlaybackType());
        assertEquals("https://player.vimeo.com/video/999", dto.getVideoUrl());
    }

    @Test
    void iframeWithoutSrcThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.saveFromUrl("<iframe width=\"100\"></iframe>", "t", "d", "admin"));
    }

    @Test
    void updateMetadataWithoutConfigThrows() {
        assertThrows(IllegalStateException.class, () -> service.updateMetadata("t", "d", "admin"));
    }

    @Test
    void removeDeletesManagedFileAndRecord() throws IOException {
        PlatformDemoVideo config = new PlatformDemoVideo();
        config.setVideoUrl("https://bucket.s3.amazonaws.com/demo-video/x.mp4");
        when(repository.findById(PlatformDemoVideo.SINGLETON_ID)).thenReturn(Optional.of(config));
        when(fileStorageService.isManagedStorageUrl(config.getVideoUrl())).thenReturn(true);

        service.remove("admin");

        verify(fileStorageService).deleteFile(config.getVideoUrl());
        verify(repository).delete(config);
    }

    @Test
    void removeIsNoOpWhenNothingConfigured() throws IOException {
        service.remove("admin");
        verify(repository, never()).delete(any());
    }

    @Test
    void savedConfigIsAvailableWithDefaultTitle() {
        DemoVideoDTO dto = service.saveFromUrl("https://cdn.example.com/demo.mp4", "  ", null, "admin");
        assertTrue(dto.isAvailable());
        assertEquals("How to use Skillama", dto.getTitle()); // default title when blank
    }
}
