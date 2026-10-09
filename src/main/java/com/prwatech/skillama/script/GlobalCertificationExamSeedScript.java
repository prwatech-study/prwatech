package com.prwatech.skillama.script;

import com.prwatech.skillama.model.CertificationTier;
import com.prwatech.skillama.model.GlobalCertificationExam;
import com.prwatech.skillama.repository.GlobalCertificationExamRepository;
import com.prwatech.skillama.service.GlobalCertificationExamService;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the GCP global certification catalog (URLs only). Guidelines snapshots are
 * fetched on admin save / refresh / first exam start so startup is not blocked by network.
 */
@Component
@RequiredArgsConstructor
public class GlobalCertificationExamSeedScript implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalCertificationExamSeedScript.class);
    private static final String PROVIDER = "GCP";
    private static final String ACTOR = "system-seed";

    private final GlobalCertificationExamRepository repository;

    @Override
    public void run(String... args) {
        int added = 0;
        for (SeedRow seed : gcpSeeds()) {
            String nameKey = GlobalCertificationExamService.nameKey(seed.name());
            if (repository.findByProviderIgnoreCaseAndNameKey(PROVIDER, nameKey).isPresent()) {
                continue;
            }
            repository.save(GlobalCertificationExam.builder()
                    .provider(PROVIDER)
                    .tier(seed.tier())
                    .name(seed.name())
                    .nameKey(nameKey)
                    .guidelinesUrl(seed.url())
                    .description(seed.description())
                    .active(true)
                    .createdAt(IndiaTime.now())
                    .createdBy(ACTOR)
                    .updatedAt(IndiaTime.now())
                    .updatedBy(ACTOR)
                    .build());
            added++;
        }
        if (added > 0) {
            LOGGER.info("Seeded {} GCP global certification exam catalog entries", added);
        }
    }

    private List<SeedRow> gcpSeeds() {
        return List.of(
                row(CertificationTier.FOUNDATIONAL, "Cloud Digital Leader",
                        "https://cloud.google.com/certification/cloud-digital-leader",
                        "Articulate Google Cloud core products and business value."),
                row(CertificationTier.FOUNDATIONAL, "Generative AI Leader",
                        "https://cloud.google.com/certification/generative-ai-leader",
                        "Lead generative AI initiatives with Google Cloud."),
                row(CertificationTier.ASSOCIATE, "Associate Cloud Engineer",
                        "https://cloud.google.com/certification/cloud-engineer",
                        "Deploy, monitor, and maintain Google Cloud projects."),
                row(CertificationTier.ASSOCIATE, "Google Workspace Administrator",
                        "https://cloud.google.com/certification/google-workspace-administrator",
                        "Plan and manage Google Workspace for an organization."),
                row(CertificationTier.ASSOCIATE, "Data Practitioner",
                        "https://cloud.google.com/certification/data-practitioner",
                        "Work with data on Google Cloud as a practitioner."),
                row(CertificationTier.PROFESSIONAL, "Professional Cloud Architect",
                        "https://cloud.google.com/certification/cloud-architect",
                        "Design and manage robust, secure, scalable Google Cloud solutions."),
                row(CertificationTier.PROFESSIONAL, "Professional Cloud Database Engineer",
                        "https://cloud.google.com/certification/cloud-database-engineer",
                        "Design and manage Google Cloud database solutions."),
                row(CertificationTier.PROFESSIONAL, "Professional Cloud Developer",
                        "https://cloud.google.com/certification/cloud-developer",
                        "Build scalable applications on Google Cloud."),
                row(CertificationTier.PROFESSIONAL, "Professional Data Engineer",
                        "https://cloud.google.com/certification/data-engineer",
                        "Design data processing systems on Google Cloud."),
                row(CertificationTier.PROFESSIONAL, "Professional Cloud DevOps Engineer",
                        "https://cloud.google.com/certification/cloud-devops-engineer",
                        "Build delivery pipelines and SRE practices on Google Cloud."),
                row(CertificationTier.PROFESSIONAL, "Professional Cloud Security Engineer",
                        "https://cloud.google.com/certification/cloud-security-engineer",
                        "Design and implement Google Cloud security."),
                row(CertificationTier.PROFESSIONAL, "Professional Cloud Network Engineer",
                        "https://cloud.google.com/certification/cloud-network-engineer",
                        "Implement Google Cloud networking."),
                row(CertificationTier.PROFESSIONAL, "Professional Machine Learning Engineer",
                        "https://cloud.google.com/certification/machine-learning-engineer",
                        "Design and build ML solutions on Google Cloud."),
                row(CertificationTier.PROFESSIONAL, "Professional Security Operations Engineer",
                        "https://cloud.google.com/certification/security-operations-engineer",
                        "Operate security detection and response on Google Cloud."),
                row(CertificationTier.PROFESSIONAL, "Professional Agentic Architect (Beta)",
                        "https://cloud.google.com/certification/agentic-architect",
                        "Design agentic AI architectures on Google Cloud.")
        );
    }

    private static SeedRow row(CertificationTier tier, String name, String url, String description) {
        return new SeedRow(tier, name, url, description);
    }

    private record SeedRow(CertificationTier tier, String name, String url, String description) {}
}
