package com.prwatech.skillama.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.OrganizationActivityLog;
import com.prwatech.skillama.model.OrganizationNotificationLog;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.repository.OrganizationActivityLogRepository;
import com.prwatech.skillama.repository.OrganizationNotificationLogRepository;
import com.prwatech.skillama.repository.OrganizationRepository;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrgNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrgNotificationService.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationActivityLogRepository activityLogRepository;
    private final OrganizationNotificationLogRepository notificationLogRepository;
    private final SkillamaUserRepository userRepository;
    private final NotificationSettingsService notificationSettingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrganizationActivityLog notify(
            String organizationId,
            String eventType,
            String summary,
            Object details,
            User actor) {
        Organization org = organizationRepository.findById(organizationId).orElse(null);
        OrganizationActivityLog log = OrganizationActivityLog.builder()
                .organizationId(organizationId)
                .eventType(eventType)
                .summary(summary)
                .detailsJson(writeDetails(details))
                .actorId(actor != null ? actor.getId() : "SYSTEM")
                .actorEmail(actor != null ? actor.getEmail() : null)
                .actorRole(actor != null && actor.getOrgRole() != null
                        ? actor.getOrgRole().name()
                        : actor != null && actor.getRole() != null ? actor.getRole().name() : "SYSTEM")
                .entityType("ORG")
                .entityId(organizationId)
                .emailSent(true)
                .createdAt(IndiaTime.now())
                .build();
        log = activityLogRepository.save(log);

        if (org != null) {
            sendOrgEmails(org, eventType, summary, log.getId());
            sendSalesTeamCopy(org, eventType, summary);
        }
        return log;
    }

    private void sendOrgEmails(Organization org, String eventType, String summary, String activityLogId) {
        Map<String, OrganizationNotificationLog.RecipientType> recipients = resolveOrgRecipients(org);
        String subject = "[" + org.getName() + "] " + summary;
        String body = summary + "\n\nOrganization: " + org.getName() + " (" + org.getSlug() + ")";
        NotificationEventType notificationType = mapToNotificationType(eventType);

        for (Map.Entry<String, OrganizationNotificationLog.RecipientType> entry : recipients.entrySet()) {
            String email = entry.getKey();
            OrganizationNotificationLog.RecipientType recipientType = entry.getValue();
            try {
                notificationSettingsService.sendLearnerNotification(notificationType, email, subject, body);
                saveNotificationLog(
                        org.getId(),
                        activityLogId,
                        eventType,
                        recipientType,
                        email,
                        subject,
                        OrganizationNotificationLog.DeliveryStatus.SENT,
                        null);
            } catch (Exception e) {
                LOGGER.warn("Failed to send org notification to {}: {}", email, e.getMessage());
                saveNotificationLog(
                        org.getId(),
                        activityLogId,
                        eventType,
                        recipientType,
                        email,
                        subject,
                        OrganizationNotificationLog.DeliveryStatus.FAILED,
                        e.getMessage());
            }
        }
    }

    private void sendSalesTeamCopy(Organization org, String eventType, String summary) {
        if (!isContractAlert(eventType)) {
            return;
        }
        String subject = "[Skillama Sales] " + org.getName() + " — " + summary;
        String body = summary + "\n\nOrganization: " + org.getName() + " (" + org.getSlug() + ")\n"
                + "Contact: " + org.getContactEmail();
        try {
            notificationSettingsService.sendTeamNotification(
                    NotificationEventType.ORG_CONTRACT_ALERT, subject, body);
        } catch (Exception e) {
            LOGGER.warn("Failed to send sales team org alert: {}", e.getMessage());
        }
    }

    private Map<String, OrganizationNotificationLog.RecipientType> resolveOrgRecipients(Organization org) {
        Map<String, OrganizationNotificationLog.RecipientType> emails = new LinkedHashMap<>();
        if (org.getContactEmail() != null && !org.getContactEmail().isBlank()) {
            emails.put(
                    org.getContactEmail().trim().toLowerCase(),
                    OrganizationNotificationLog.RecipientType.ORG_CONTACT);
        }
        if (org.getSalesContactEmail() != null && !org.getSalesContactEmail().isBlank()) {
            emails.put(
                    org.getSalesContactEmail().trim().toLowerCase(),
                    OrganizationNotificationLog.RecipientType.SALES_TEAM);
        }
        if (org.getRootOwnerUserId() != null) {
            userRepository.findById(org.getRootOwnerUserId())
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .ifPresent(e -> emails.put(
                            e.trim().toLowerCase(),
                            OrganizationNotificationLog.RecipientType.ORG_OWNER));
        }
        return emails;
    }

    private void saveNotificationLog(
            String organizationId,
            String activityLogId,
            String eventType,
            OrganizationNotificationLog.RecipientType recipientType,
            String recipientEmail,
            String subject,
            OrganizationNotificationLog.DeliveryStatus status,
            String failureReason) {
        notificationLogRepository.save(OrganizationNotificationLog.builder()
                .organizationId(organizationId)
                .activityLogId(activityLogId)
                .eventType(eventType)
                .recipientType(recipientType)
                .recipientEmail(recipientEmail)
                .subject(subject)
                .status(status)
                .failureReason(failureReason)
                .sentAt(IndiaTime.now())
                .build());
    }

    private String writeDetails(Object details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            return String.valueOf(details);
        }
    }

    private NotificationEventType mapToNotificationType(String eventType) {
        if (isContractAlert(eventType)) {
            return NotificationEventType.ORG_CONTRACT_ALERT;
        }
        return NotificationEventType.ORG_LIFECYCLE;
    }

    private boolean isContractAlert(String eventType) {
        return eventType != null && eventType.startsWith("ORG_CONTRACT_");
    }
}
