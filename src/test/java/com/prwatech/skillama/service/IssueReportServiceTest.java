package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.model.IssueReport;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.repository.IssueReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueReportServiceTest {

    @Mock private IssueReportRepository issueReportRepository;
    @Mock private NotificationSettingsService notificationSettingsService;
    @Mock private UserService userService;

    private IssueReportService service;

    @BeforeEach
    void setUp() {
        service = new IssueReportService(
                issueReportRepository,
                new ObjectMapper(),
                notificationSettingsService,
                userService);
        lenient().when(issueReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateStatus_newReply_emailsTheReporter() {
        IssueReport issue = openIssue("i1", "a@b.com", "Quiz will not start");
        when(issueReportRepository.findById("i1")).thenReturn(Optional.of(issue));
        when(notificationSettingsService.isEnabled(NotificationEventType.ISSUE_REPLY)).thenReturn(true);

        IssueReport saved = service.updateStatus("i1", "IN_PROGRESS", "Try again after a refresh.", "admin-1");

        assertEquals("IN_PROGRESS", saved.getStatus());
        assertEquals("Try again after a refresh.", saved.getAdminReply());
        assertEquals("admin-1", saved.getRepliedBy());
        assertTrue(saved.getReplyEmailDelivered());
        verify(notificationSettingsService).sendLearnerNotification(
                eq(NotificationEventType.ISSUE_REPLY),
                eq("a@b.com"),
                eq("Reply to the issue you reported on Skillama"),
                contains("Try again after a refresh."));
    }

    @Test
    void updateStatus_replyAndResolve_sendsOneReplyEmail() {
        IssueReport issue = openIssue("i1", "a@b.com", "Time exhausted");
        when(issueReportRepository.findById("i1")).thenReturn(Optional.of(issue));
        when(notificationSettingsService.isEnabled(NotificationEventType.ISSUE_REPLY)).thenReturn(true);

        IssueReport saved = service.updateStatus("i1", "RESOLVED", "Your daily limit resets at midnight IST.", "admin-1");

        assertEquals("RESOLVED", saved.getStatus());
        assertTrue(saved.getReplyEmailDelivered());
        verify(notificationSettingsService).sendLearnerNotification(
                eq(NotificationEventType.ISSUE_REPLY),
                eq("a@b.com"),
                eq("We replied to the issue you reported"),
                contains("marked it resolved"));
    }

    @Test
    void updateStatus_sameReplyAgain_doesNotEmailTwice() {
        IssueReport issue = openIssue("i1", "a@b.com", "Quiz will not start");
        issue.setAdminReply("Already sent.");
        when(issueReportRepository.findById("i1")).thenReturn(Optional.of(issue));

        IssueReport saved = service.updateStatus("i1", "IN_PROGRESS", "Already sent.", "admin-1");

        assertEquals(null, saved.getReplyEmailDelivered());
        verify(notificationSettingsService, never())
                .sendLearnerNotification(any(), anyString(), anyString(), anyString());
    }

    @Test
    void updateStatus_resolveWithoutReply_doesNotEmail() {
        IssueReport issue = openIssue("i1", "a@b.com", "Python course will not start");
        when(issueReportRepository.findById("i1")).thenReturn(Optional.of(issue));

        IssueReport saved = service.updateStatus("i1", "RESOLVED", null, "admin-1");

        assertEquals("RESOLVED", saved.getStatus());
        assertEquals(null, saved.getReplyEmailDelivered());
        verify(notificationSettingsService, never())
                .sendLearnerNotification(any(), anyString(), anyString(), anyString());
    }

    @Test
    void updateStatus_closeWithNote_doesNotEmail() {
        IssueReport issue = openIssue("i1", "a@b.com", "not start python course");
        when(issueReportRepository.findById("i1")).thenReturn(Optional.of(issue));

        IssueReport saved = service.updateStatus(
                "i1", "CLOSED", null, "Duplicate of the credits ticket.", false, "admin-1");

        assertEquals("CLOSED", saved.getStatus());
        assertEquals("Duplicate of the credits ticket.", saved.getInternalNote());
        assertEquals(null, saved.getReplyEmailDelivered());
        verify(notificationSettingsService, never())
                .sendLearnerNotification(any(), anyString(), anyString(), anyString());
    }

    @Test
    void updateStatus_missingEmail_looksUpTheAccount() {
        IssueReport issue = openIssue("i1", null, "Cannot open the course");
        issue.setReporterUserId("user-9");
        User user = new User();
        user.setEmail("found@x.com");
        when(issueReportRepository.findById("i1")).thenReturn(Optional.of(issue));
        when(userService.findById("user-9")).thenReturn(Optional.of(user));
        when(notificationSettingsService.isEnabled(NotificationEventType.ISSUE_REPLY)).thenReturn(true);

        IssueReport saved = service.updateStatus("i1", "OPEN", "We reassigned the course.", "admin-1");

        assertEquals("found@x.com", saved.getReporterEmail());
        assertTrue(saved.getReplyEmailDelivered());
        verify(notificationSettingsService).sendLearnerNotification(
                eq(NotificationEventType.ISSUE_REPLY),
                eq("found@x.com"),
                anyString(),
                contains("We reassigned the course."));
    }

    @Test
    void updateStatus_noEmailAnywhere_savesReplyWithoutMailing() {
        IssueReport issue = openIssue("i1", null, "Guest report");
        when(issueReportRepository.findById("i1")).thenReturn(Optional.of(issue));

        IssueReport saved = service.updateStatus("i1", "OPEN", "Thanks, we saw this.", "admin-1");

        assertEquals("Thanks, we saw this.", saved.getAdminReply());
        assertFalse(saved.getReplyEmailDelivered());
        verify(notificationSettingsService, never())
                .sendLearnerNotification(any(), anyString(), anyString(), anyString());
    }

    @Test
    void updateStatus_notificationsOff_savesReplyAndReportsNotEmailed() {
        IssueReport issue = openIssue("i1", "a@b.com", "Quiz will not start");
        when(issueReportRepository.findById("i1")).thenReturn(Optional.of(issue));
        when(notificationSettingsService.isEnabled(NotificationEventType.ISSUE_REPLY)).thenReturn(false);

        IssueReport saved = service.updateStatus("i1", "OPEN", "Looking into it.", "admin-1");

        assertFalse(saved.getReplyEmailDelivered());
        verify(notificationSettingsService, never())
                .sendLearnerNotification(any(), anyString(), anyString(), anyString());
    }

    @Test
    void updateStatus_unknownIssue_throws() {
        when(issueReportRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus("missing", "OPEN", "Hi", "admin-1"));
    }

    private static IssueReport openIssue(String id, String email, String description) {
        IssueReport issue = new IssueReport();
        issue.setId(id);
        issue.setStatus("OPEN");
        issue.setReporterEmail(email);
        issue.setUserDescription(description);
        return issue;
    }
}
