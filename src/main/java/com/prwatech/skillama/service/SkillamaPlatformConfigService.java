package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.UpgradeContactDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SkillamaPlatformConfigService {

    @Value("${skillama.upgrade.contact-email:hello@skillama.co.in}")
    private String upgradeContactEmail;

    @Value("${skillama.upgrade.contact-message:Paid and enterprise access is provisioned by the internal Skillama team. Email us with the account you use to sign in.}")
    private String upgradeContactMessage;

    @Value("${skillama.upgrade.mailto-subject:Skillama full access request}")
    private String upgradeMailtoSubject;

    public UpgradeContactDTO getUpgradeContact() {
        return UpgradeContactDTO.builder()
                .contactEmail(upgradeContactEmail.trim())
                .contactMessage(upgradeContactMessage)
                .mailtoSubject(upgradeMailtoSubject)
                .build();
    }

    public String getUpgradeContactEmail() {
        return upgradeContactEmail.trim();
    }
}
