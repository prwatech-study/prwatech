package com.prwatech.skillama.service;

import com.mongodb.client.result.UpdateResult;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.model.UserSession;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UserLoginEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Single-active-session enforcement: startNewSession/logout must go through an atomic
 * findAndModify (not read-then-save), so two near-simultaneous logins can't both read the
 * same tokenVersion and both end up minting a token that stays valid.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceSessionTest {

    @Mock private SkillamaUserRepository userRepository;
    @Mock private UserLoginEventRepository userLoginEventRepository;
    @Mock private EmailServiceImpl emailService;
    @Mock private AppContext appContext;
    @Mock private PasswordEncode passwordEncode;
    @Mock private NotificationSettingsService notificationSettingsService;
    @Mock private UserContactService userContactService;
    @Mock private MongoTemplate skillamaMongoTemplate;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                userLoginEventRepository,
                emailService,
                appContext,
                passwordEncode,
                notificationSettingsService,
                userContactService,
                skillamaMongoTemplate);
    }

    @Test
    void startNewSession_returnsBumpedVersionFromAtomicUpdate() {
        User updated = User.builder().id("u1").tokenVersion(3).sessionActive(true).build();
        when(skillamaMongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(updated);

        int version = userService.startNewSession("u1");

        assertEquals(3, version);
    }

    @Test
    void startNewSession_missingUser_returnsZeroInsteadOfThrowing() {
        when(skillamaMongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(null);

        int version = userService.startNewSession("missing-user");

        assertEquals(0, version);
    }

    @Test
    void startNewSession_incrementsTokenVersionAndMarksSessionActive() {
        when(skillamaMongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(User.builder().id("u1").tokenVersion(1).build());

        userService.startNewSession("u1");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(skillamaMongoTemplate).findAndModify(
                any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(User.class));
        var updateDoc = updateCaptor.getValue().getUpdateObject();
        assertEquals(1, updateDoc.get("$inc", org.bson.Document.class).get("tokenVersion"));
        assertEquals(true, updateDoc.get("$set", org.bson.Document.class).get("sessionActive"));
    }

    @Test
    void logout_incrementsTokenVersionAndClearsSessionActive() {
        userService.logout("u1");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(skillamaMongoTemplate).findAndModify(
                any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(User.class));
        var updateDoc = updateCaptor.getValue().getUpdateObject();
        assertEquals(1, updateDoc.get("$inc", org.bson.Document.class).get("tokenVersion"));
        assertEquals(false, updateDoc.get("$set", org.bson.Document.class).get("sessionActive"));
    }

    // --- Active-time tracking (UserSession rows) ---

    @Test
    void startNewSession_opensANewUserSessionRow() {
        when(skillamaMongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(User.builder().id("u1").tokenVersion(5).build());

        userService.startNewSession("u1");

        ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
        verify(skillamaMongoTemplate).insert(sessionCaptor.capture());
        UserSession opened = sessionCaptor.getValue();
        assertEquals("u1", opened.getUserId());
        assertEquals(5, opened.getTokenVersion());
        assertNotNull(opened.getStartedAt());
        assertEquals(opened.getStartedAt(), opened.getLastHeartbeatAt());
        assertNull(opened.getEndedAt());
    }

    @Test
    void startNewSession_closesAnyPriorOpenSessionAsReplaced() {
        when(skillamaMongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(User.builder().id("u1").tokenVersion(2).build());

        userService.startNewSession("u1");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(skillamaMongoTemplate).updateMulti(
                any(Query.class), updateCaptor.capture(), eq(UserSession.class));
        var updateDoc = updateCaptor.getValue().getUpdateObject();
        assertEquals("REPLACED", updateDoc.get("$set", org.bson.Document.class).get("endReason"));
        assertNotNull(updateDoc.get("$set", org.bson.Document.class).get("endedAt"));
    }

    @Test
    void logout_closesOpenSessionAsLoggedOut() {
        userService.logout("u1");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(skillamaMongoTemplate).updateMulti(
                any(Query.class), updateCaptor.capture(), eq(UserSession.class));
        var updateDoc = updateCaptor.getValue().getUpdateObject();
        assertEquals("LOGOUT", updateDoc.get("$set", org.bson.Document.class).get("endReason"));
    }

    @Test
    void recordHeartbeat_matchingOpenSession_updatesLastHeartbeatAndReturnsTrue() {
        UpdateResult result = mock(UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(1L);
        when(skillamaMongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(UserSession.class)))
                .thenReturn(result);

        assertTrue(userService.recordHeartbeat("u1", 3));
    }

    @Test
    void recordHeartbeat_sessionAlreadyReplacedOrLoggedOut_returnsFalse() {
        UpdateResult result = mock(UpdateResult.class);
        lenient().when(result.getModifiedCount()).thenReturn(0L);
        when(skillamaMongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(UserSession.class)))
                .thenReturn(result);

        assertFalse(userService.recordHeartbeat("u1", 3));
    }

    @Test
    void getTotalActiveSeconds_sumsLastHeartbeatMinusStartedAtAcrossSessions() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 1, 9, 0, 0);
        UserSession fifteenMinutes = UserSession.builder()
                .startedAt(base).lastHeartbeatAt(base.plusMinutes(15)).build();
        UserSession fiveMinutes = UserSession.builder()
                .startedAt(base.plusHours(2)).lastHeartbeatAt(base.plusHours(2).plusMinutes(5)).build();
        // No heartbeat ever recorded past the login instant (e.g. tab closed immediately) — contributes 0.
        UserSession neverHeartbeat = UserSession.builder()
                .startedAt(base.plusHours(4)).lastHeartbeatAt(null).build();
        when(skillamaMongoTemplate.find(any(Query.class), eq(UserSession.class)))
                .thenReturn(List.of(fifteenMinutes, fiveMinutes, neverHeartbeat));

        long totalSeconds = userService.getTotalActiveSeconds("u1", base.minusDays(1), base.plusDays(1));

        assertEquals(20 * 60, totalSeconds);
    }
}
