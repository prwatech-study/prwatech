package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.SalesInterestRequestDTO;
import com.prwatech.skillama.dto.UpdateSalesLeadDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.SalesLead;
import com.prwatech.skillama.repository.SalesLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prwatech.skillama.util.IndiaTime;

@Service
@RequiredArgsConstructor
public class SalesLeadService {

    private final SalesLeadRepository salesLeadRepository;

    public SalesLead createLead(SalesInterestRequestDTO request) {
        if (request.getConsentContact() == null || !request.getConsentContact()) {
            throw new IllegalArgumentException("consentContact must be true to submit");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        SalesLead lead = SalesLead.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .message(request.getMessage())
                .consentContact(true)
                .status(SalesLead.LeadStatus.NEW)
                .createdAt(IndiaTime.now())
                .build();
        return salesLeadRepository.save(lead);
    }

    public Page<SalesLead> listLeads(int page, int size, SalesLead.LeadStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null) {
            return salesLeadRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return salesLeadRepository.findAll(pageable);
    }

    @Transactional
    public SalesLead update(String leadId, UpdateSalesLeadDTO body, String adminId) {
        SalesLead lead = salesLeadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Sales lead not found"));
        if (body.getStatus() != null) {
            lead.setStatus(body.getStatus());
            if (body.getStatus() == SalesLead.LeadStatus.CONTACTED) {
                lead.setContactedAt(IndiaTime.now());
                lead.setContactedByAdminId(adminId);
            }
        }
        if (body.getNotes() != null) {
            lead.setNotes(body.getNotes());
        }
        lead.setUpdatedAt(IndiaTime.now());
        return salesLeadRepository.save(lead);
    }
}
