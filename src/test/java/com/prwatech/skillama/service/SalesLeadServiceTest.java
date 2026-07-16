package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.SalesInterestRequestDTO;
import com.prwatech.skillama.model.SalesLead;
import com.prwatech.skillama.repository.SalesLeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesLeadServiceTest {

    @Mock private SalesLeadRepository salesLeadRepository;

    private SalesLeadService service;

    @BeforeEach
    void setUp() {
        service = new SalesLeadService(salesLeadRepository);
    }

    private SalesInterestRequestDTO request(Boolean consent, String email) {
        SalesInterestRequestDTO r = new SalesInterestRequestDTO();
        r.setConsentContact(consent);
        r.setEmail(email);
        r.setName("Asha");
        r.setPhone("+919876543210");
        return r;
    }

    @Test
    void rejectsWithoutConsent() {
        assertThrows(IllegalArgumentException.class, () -> service.createLead(request(null, "a@x.com")));
        assertThrows(IllegalArgumentException.class, () -> service.createLead(request(false, "a@x.com")));
    }

    @Test
    void rejectsMissingEmail() {
        assertThrows(IllegalArgumentException.class, () -> service.createLead(request(true, "  ")));
        assertThrows(IllegalArgumentException.class, () -> service.createLead(request(true, null)));
    }

    @Test
    void createsLeadWithConsentStampedTrue() {
        when(salesLeadRepository.save(any(SalesLead.class))).thenAnswer(inv -> inv.getArgument(0));
        SalesLead lead = service.createLead(request(true, "a@x.com"));
        assertEquals("a@x.com", lead.getEmail());
        assertTrue(lead.isConsentContact());
        assertEquals("Asha", lead.getName());
    }
}
