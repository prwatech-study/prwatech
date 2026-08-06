package com.prwatech.skillama.service;

import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.configuration.PasswordEncode;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.model.User;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}
