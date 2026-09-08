package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.OrganizationActivityLogDTO;
import com.prwatech.skillama.model.OrganizationActivityLog;
import com.prwatech.skillama.repository.OrganizationActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrgActivityService {

    private final OrganizationActivityLogRepository activityLogRepository;

    public Page<OrganizationActivityLogDTO> listActivity(String organizationId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return activityLogRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable)
                .map(this::toDto);
    }

    private OrganizationActivityLogDTO toDto(OrganizationActivityLog log) {
        return OrganizationActivityLogDTO.builder()
                .id(log.getId())
                .eventType(log.getEventType())
                .summary(log.getSummary())
                .actorEmail(log.getActorEmail())
                .actorRole(log.getActorRole())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
