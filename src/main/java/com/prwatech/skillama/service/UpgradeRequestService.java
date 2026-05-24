package com.prwatech.skillama.service;

import com.prwatech.common.dto.EmailSendDto;
import com.prwatech.common.service.impl.EmailServiceImpl;
import com.prwatech.skillama.dto.UpgradeInterestRequestDTO;
import com.prwatech.skillama.notification.NotificationEventType;
import com.prwatech.skillama.dto.UpgradeRequestDTO;
import com.prwatech.skillama.dto.UpdateUpgradeRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.UpgradeRequest;
import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import com.prwatech.skillama.repository.UpgradeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;

@Service
@RequiredArgsConstructor
public class UpgradeRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeRequestService.class);

    private final UpgradeRequestRepository upgradeRequestRepository;
    private final SkillamaUserRepository userRepository;
    private final EmailServiceImpl emailService;
    private final SkillamaPlatformConfigService platformConfigService;
    private final NotificationSettingsService notificationSettingsService;

    @Transactional
    public UpgradeRequestDTO recordInterest(String userId, UpgradeInterestRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UpgradeRequest entry = UpgradeRequest.builder()
                .userId(userId)
                .userName(user.getName())
                .userEmail(user.getEmail())
                .userPhone(user.getPhone())
                .source(request != null && request.getSource() != null ? request.getSource() : "PROFILE")
                .courseId(request != null ? request.getCourseId() : null)
                .courseName(request != null ? request.getCourseName() : null)
                .planTier(request != null && request.getPlanTier() != null
                        ? request.getPlanTier()
                        : user.getPlanTier())
                .queryCreditsUsed(request != null && request.getQueryCreditsUsed() != null
                        ? request.getQueryCreditsUsed()
                        : user.getQueryCreditsUsed())
                .queryCreditsLimit(request != null && request.getQueryCreditsLimit() != null
                        ? request.getQueryCreditsLimit()
                        : user.getQueryCreditsLimit())
                .message(request != null ? request.getMessage() : null)
                .status(UpgradeRequest.RequestStatus.NEW)
                .createdAt(IndiaTime.now())
                .updatedAt(IndiaTime.now())
                .build();

        entry = upgradeRequestRepository.save(entry);
        notifySalesTeam(entry, user);
        return toDto(entry);
    }

    private void notifySalesTeam(UpgradeRequest entry, User user) {
        String subject = "Skillama upgrade request: " + (user.getEmail() != null ? user.getEmail() : user.getId());
        String body = "A freemium user requested full access.\n\n"
                + "Name: " + user.getName() + "\n"
                + "Email: " + user.getEmail() + "\n"
                + "Phone: " + (user.getPhone() != null ? user.getPhone() : "—") + "\n"
                + "Source: " + entry.getSource() + "\n"
                + "Course: " + (entry.getCourseName() != null ? entry.getCourseName() : "—") + "\n"
                + "Credits: " + entry.getQueryCreditsUsed() + " / " + entry.getQueryCreditsLimit() + "\n"
                + "Request ID: " + entry.getId() + "\n"
                + "Contact: " + platformConfigService.getUpgradeContactEmail() + "\n";
        try {
            notificationSettingsService.sendTeamNotification(
                    NotificationEventType.UPGRADE_REQUEST, subject, body);
        } catch (Exception e) {
            LOGGER.warn("Could not email sales team for upgrade request {}: {}", entry.getId(), e.getMessage());
        }
    }

    public Page<UpgradeRequestDTO> list(int page, int size, UpgradeRequest.RequestStatus status, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UpgradeRequest> result;
        if (search != null && !search.isBlank()) {
            result = upgradeRequestRepository.searchByUser(search.trim(), pageable);
        } else if (status != null) {
            result = upgradeRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            result = upgradeRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return result.map(this::toDto);
    }

    @Transactional
    public UpgradeRequestDTO update(String requestId, UpdateUpgradeRequestDTO body, String adminId) {
        UpgradeRequest entry = upgradeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Upgrade request not found"));
        if (body.getStatus() != null) {
            entry.setStatus(body.getStatus());
            if (body.getStatus() == UpgradeRequest.RequestStatus.CONTACTED) {
                entry.setContactedAt(IndiaTime.now());
                entry.setContactedByAdminId(adminId);
            }
        }
        if (body.getNotes() != null) {
            entry.setNotes(body.getNotes());
        }
        entry.setUpdatedAt(IndiaTime.now());
        return toDto(upgradeRequestRepository.save(entry));
    }

    private UpgradeRequestDTO toDto(UpgradeRequest r) {
        return UpgradeRequestDTO.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .userName(r.getUserName())
                .userEmail(r.getUserEmail())
                .userPhone(r.getUserPhone())
                .source(r.getSource())
                .courseId(r.getCourseId())
                .courseName(r.getCourseName())
                .planTier(r.getPlanTier())
                .queryCreditsUsed(r.getQueryCreditsUsed())
                .queryCreditsLimit(r.getQueryCreditsLimit())
                .message(r.getMessage())
                .status(r.getStatus())
                .notes(r.getNotes())
                .contactedByAdminId(r.getContactedByAdminId())
                .contactedAt(r.getContactedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
