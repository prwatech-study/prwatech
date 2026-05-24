package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.SalesInterestRequestDTO;
import com.prwatech.skillama.model.SalesLead;
import com.prwatech.skillama.repository.SalesLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
                .createdAt(IndiaTime.now())
                .build();
        return salesLeadRepository.save(lead);
    }

    public Page<SalesLead> listLeads(int page, int size) {
        return salesLeadRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
