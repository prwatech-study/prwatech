package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.LmsThemeStatsDTO;
import com.prwatech.skillama.dto.LmsThemeSwitchRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.LmsThemeEvent;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.LmsThemeEventRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LmsThemeServiceTest {

    @Mock private LmsThemeEventRepository lmsThemeEventRepository;
    @Mock private SkillamaUserRepository userRepository;

    private LmsThemeService service;

    @BeforeEach
    void setUp() {
        service = new LmsThemeService(lmsThemeEventRepository, userRepository);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private LmsThemeSwitchRequestDTO req(String theme, String previous) {
        LmsThemeSwitchRequestDTO r = new LmsThemeSwitchRequestDTO();
        r.setTheme(theme);
        r.setPreviousTheme(previous);
        return r;
    }

    @Test
    void recordSwitchUnknownUserThrows() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.recordThemeSwitch("ghost", req("aurora", null)));
    }

    @Test
    void recordSwitchRejectsInvalidTheme() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().id("u1").build()));
        assertThrows(IllegalArgumentException.class, () -> service.recordThemeSwitch("u1", req("neon", null)));
    }

    @Test
    void recordSwitchNoOpWhenThemeUnchanged() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().id("u1").build()));
        service.recordThemeSwitch("u1", req("aurora", "aurora"));
        verify(lmsThemeEventRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void recordSwitchPersistsEventAndUserPreference() {
        User user = User.builder().id("u1").email("u@x.com").build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        LmsThemeSwitchRequestDTO r = req("aurora", "classic");
        r.setPagePath("/lms/dashboard");

        service.recordThemeSwitch("u1", r);

        ArgumentCaptor<LmsThemeEvent> captor = ArgumentCaptor.forClass(LmsThemeEvent.class);
        verify(lmsThemeEventRepository).save(captor.capture());
        assertEquals("aurora", captor.getValue().getTheme());
        assertEquals("lms", captor.getValue().getContext()); // inferred from path
        assertEquals("aurora", user.getLmsThemePreference());
        verify(userRepository).save(user);
    }

    @Test
    void visitorSwitchRequiresVisitorId() {
        LmsThemeSwitchRequestDTO r = req("aurora", null);
        r.setContext("homepage");
        assertThrows(IllegalArgumentException.class, () -> service.recordVisitorThemeSwitch(r));
    }

    @Test
    void visitorSwitchOnlyAllowedOnHomepage() {
        LmsThemeSwitchRequestDTO r = req("aurora", null);
        r.setVisitorId("v1");
        r.setContext("lms");
        assertThrows(IllegalArgumentException.class, () -> service.recordVisitorThemeSwitch(r));
    }

    @Test
    void visitorSwitchPersistsAnonymousEvent() {
        LmsThemeSwitchRequestDTO r = req("classic", "aurora");
        r.setVisitorId("v1");
        r.setContext("homepage");

        service.recordVisitorThemeSwitch(r);

        ArgumentCaptor<LmsThemeEvent> captor = ArgumentCaptor.forClass(LmsThemeEvent.class);
        verify(lmsThemeEventRepository).save(captor.capture());
        assertEquals("v1", captor.getValue().getVisitorId());
        assertEquals(true, captor.getValue().isAnonymous());
    }

    @Test
    void recordSwitchPersistsObsidianPreference() {
        User user = User.builder().id("u1").email("u@x.com").build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        LmsThemeSwitchRequestDTO r = req("obsidian", "aurora");
        r.setContext("homepage");

        service.recordThemeSwitch("u1", r);

        ArgumentCaptor<LmsThemeEvent> captor = ArgumentCaptor.forClass(LmsThemeEvent.class);
        verify(lmsThemeEventRepository).save(captor.capture());
        assertEquals("obsidian", captor.getValue().getTheme());
        assertEquals("homepage", captor.getValue().getContext());
        assertEquals("obsidian", user.getLmsThemePreference());
    }

    @Test
    void visitorSwitchAcceptsObsidian() {
        LmsThemeSwitchRequestDTO r = req("obsidian", "classic");
        r.setVisitorId("v1");
        r.setContext("homepage");

        service.recordVisitorThemeSwitch(r);

        ArgumentCaptor<LmsThemeEvent> captor = ArgumentCaptor.forClass(LmsThemeEvent.class);
        verify(lmsThemeEventRepository).save(captor.capture());
        assertEquals("obsidian", captor.getValue().getTheme());
        assertEquals(true, captor.getValue().isAnonymous());
    }

    @Test
    void statsAggregatesCounts() {
        when(lmsThemeEventRepository.countByTheme("classic")).thenReturn(3L);
        when(lmsThemeEventRepository.countByTheme("aurora")).thenReturn(7L);
        when(lmsThemeEventRepository.countByTheme("obsidian")).thenReturn(2L);
        when(userRepository.countByLmsThemePreference("obsidian")).thenReturn(4L);
        when(lmsThemeEventRepository.countByThemeAndContext("obsidian", "homepage")).thenReturn(1L);
        when(lmsThemeEventRepository.countByThemeAndContext("obsidian", "lms")).thenReturn(1L);
        when(lmsThemeEventRepository.countByThemeAndAnonymousTrue("obsidian")).thenReturn(1L);
        LmsThemeStatsDTO stats = service.getStats();
        assertEquals(3L, stats.getClassic());
        assertEquals(7L, stats.getAurora());
        assertEquals(2L, stats.getObsidian());
        assertEquals(12L, stats.getTotalSwitches());
        assertEquals(4L, stats.getActiveObsidian());
        assertEquals(1L, stats.getHomepageObsidian());
        assertEquals(1L, stats.getLmsObsidian());
        assertEquals(1L, stats.getVisitorObsidian());
    }
}
