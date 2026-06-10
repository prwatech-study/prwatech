package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.UpdateUpgradeContactDTO;
import com.prwatech.skillama.dto.UpgradeContactDTO;
import com.prwatech.skillama.model.PlatformUpgradeContact;
import com.prwatech.skillama.repository.PlatformUpgradeContactRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SkillamaPlatformConfigService {

    private final PlatformUpgradeContactRepository upgradeContactRepository;

    @Value("${skillama.upgrade.contact-email:hello@skillama.co.in}")
    private String defaultUpgradeContactEmail;

    @Value("${skillama.upgrade.contact-message:Paid and enterprise access is provisioned by the internal Skillama team. Email us with the account you use to sign in.}")
    private String defaultUpgradeContactMessage;

    @Value("${skillama.upgrade.mailto-subject:Skillama full access request}")
    private String defaultUpgradeMailtoSubject;

    public UpgradeContactDTO getUpgradeContact() {
        return upgradeContactRepository.findById(PlatformUpgradeContact.SINGLETON_ID)
                .map(this::toDto)
                .orElseGet(this::defaultDto);
    }

    public UpgradeContactDTO updateUpgradeContact(UpdateUpgradeContactDTO body, String ownerUserId) {
        if (body == null || !StringUtils.hasText(body.getContactEmail())) {
            throw new IllegalArgumentException("contactEmail is required");
        }
        String email = body.getContactEmail().trim();
        if (!email.contains("@")) {
            throw new IllegalArgumentException("contactEmail must be a valid email address");
        }

        PlatformUpgradeContact config = upgradeContactRepository
                .findById(PlatformUpgradeContact.SINGLETON_ID)
                .orElse(new PlatformUpgradeContact());
        config.setId(PlatformUpgradeContact.SINGLETON_ID);
        config.setContactEmail(email);
        config.setContactMessage(
                body.getContactMessage() != null ? body.getContactMessage().trim() : "");
        config.setMailtoSubject(
                StringUtils.hasText(body.getMailtoSubject())
                        ? body.getMailtoSubject().trim()
                        : defaultUpgradeMailtoSubject);
        config.setUpdatedAt(IndiaTime.now());
        config.setUpdatedBy(ownerUserId);
        return toDto(upgradeContactRepository.save(config));
    }

    public String getUpgradeContactEmail() {
        return getUpgradeContact().getContactEmail();
    }

    private UpgradeContactDTO defaultDto() {
        return UpgradeContactDTO.builder()
                .contactEmail(defaultUpgradeContactEmail.trim())
                .contactMessage(defaultUpgradeContactMessage)
                .mailtoSubject(defaultUpgradeMailtoSubject)
                .build();
    }

    private UpgradeContactDTO toDto(PlatformUpgradeContact config) {
        return UpgradeContactDTO.builder()
                .contactEmail(config.getContactEmail())
                .contactMessage(config.getContactMessage())
                .mailtoSubject(
                        StringUtils.hasText(config.getMailtoSubject())
                                ? config.getMailtoSubject()
                                : defaultUpgradeMailtoSubject)
                .build();
    }
}
