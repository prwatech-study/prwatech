package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CertificationExamMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialCertificationExamFormatTest {

    @Test
    void looksUpByOfficialUrlPath() {
        var architect = OfficialCertificationExamFormat.lookup(
                null, "https://cloud.google.com/learn/certification/cloud-architect").orElseThrow();
        assertEquals(120, architect.durationMinutes());
        assertEquals(50, architect.questionCountMin());
        assertEquals(60, architect.questionCountMax());

        var data = OfficialCertificationExamFormat.lookup(
                "Professional Data Engineer",
                "https://cloud.google.com/certification/data-engineer").orElseThrow();
        assertEquals(120, data.durationMinutes());
        assertEquals(40, data.questionCountMin());
        assertEquals(50, data.questionCountMax());

        var agentic = OfficialCertificationExamFormat.lookup(
                "Professional Agentic Architect (Beta)",
                "https://cloud.google.com/certification/agentic-architect").orElseThrow();
        assertEquals(180, agentic.durationMinutes());
        assertEquals(80, agentic.questionCountMin());
        assertEquals(80, agentic.questionCountMax());
    }

    @Test
    void replacesGenericNinetyFiftySixtyWithOfficialWhenTheyDiffer() {
        CertificationExamMeta storedDefault = CertificationExamMeta.builder()
                .durationMinutes(90)
                .questionCountMin(50)
                .questionCountMax(60)
                .build();

        CertificationExamMeta ace = OfficialCertificationExamFormat.merge(
                "Associate Cloud Engineer",
                "https://cloud.google.com/certification/cloud-engineer",
                storedDefault);
        assertEquals(120, ace.getDurationMinutes());
        assertEquals(50, ace.getQuestionCountMin());
        assertEquals(60, ace.getQuestionCountMax());

        CertificationExamMeta data = OfficialCertificationExamFormat.merge(
                "Professional Data Engineer",
                "https://cloud.google.com/certification/data-engineer",
                storedDefault);
        assertEquals(120, data.getDurationMinutes());
        assertEquals(40, data.getQuestionCountMin());
        assertEquals(50, data.getQuestionCountMax());

        CertificationExamMeta digitalLeader = OfficialCertificationExamFormat.merge(
                "Cloud Digital Leader",
                "https://cloud.google.com/certification/cloud-digital-leader",
                storedDefault);
        assertEquals(90, digitalLeader.getDurationMinutes());
        assertEquals(50, digitalLeader.getQuestionCountMin());
        assertEquals(60, digitalLeader.getQuestionCountMax());
    }

    @Test
    void ignoresRenewalExamSizeWhenOfficialStandardIsKnown() {
        CertificationExamMeta renewal = CertificationExamMeta.builder()
                .durationMinutes(45)
                .questionCountMin(20)
                .questionCountMax(20)
                .build();
        CertificationExamMeta merged = OfficialCertificationExamFormat.merge(
                "Cloud Digital Leader",
                "https://cloud.google.com/certification/cloud-digital-leader",
                renewal);
        assertEquals(90, merged.getDurationMinutes());
        assertEquals(50, merged.getQuestionCountMin());
        assertEquals(60, merged.getQuestionCountMax());
    }

    @Test
    void keepsASuccessfulParseThatAlreadyMatchesHours() {
        CertificationExamMeta parsed = CertificationExamMeta.builder()
                .durationMinutes(120)
                .questionCountMin(50)
                .questionCountMax(60)
                .build();
        CertificationExamMeta merged = OfficialCertificationExamFormat.merge(
                "Associate Cloud Engineer",
                "https://cloud.google.com/certification/cloud-engineer",
                parsed);
        assertEquals(120, merged.getDurationMinutes());
        assertTrue(OfficialCertificationExamFormat.sameExamRules(parsed, merged));
    }
}
