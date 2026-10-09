package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CertificationExamMeta;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificationGuidelinesFetcherTest {

    private final CertificationGuidelinesFetcher fetcher = new CertificationGuidelinesFetcher();

    private CertificationExamMeta parse(String html) {
        Document doc = Jsoup.parse(html);
        return fetcher.parseMeta(doc, doc.body().text());
    }

    @Test
    void usesSectionHeadingsAndIgnoresMarketingBullets() {
        CertificationExamMeta meta = parse("""
                <h2>Section 1: Digital transformation with Google Cloud (~17% of the exam)</h2>
                <h2>Section 2: Exploring data transformation with Google Cloud (~16% of the exam)</h2>
                <ul>
                  <li>Prepare with Certification Prep webinars Watch Cloud OnAir</li>
                  <li>Register for the Google Cloud exam today</li>
                  <li>Cloud computing basics and shared infrastructure</li>
                </ul>
                <p>Length: 90 minutes. 50-60 multiple choice and multiple select questions.</p>
                """);

        assertEquals(List.of(
                "Section 1: Digital transformation with Google Cloud (~17% of the exam)",
                "Section 2: Exploring data transformation with Google Cloud (~16% of the exam)"),
                meta.getDomains());
    }

    @Test
    void recognisesPercentageWeightedBulletsAsSections() {
        CertificationExamMeta meta = parse("""
                <ul>
                  <li>Designing and planning a cloud solution architecture (~24%)</li>
                  <li>Managing and provisioning infrastructure (~15%)</li>
                  <li>Watch Cloud OnAir webinars</li>
                </ul>
                """);

        assertEquals(2, meta.getDomains().size());
        assertTrue(meta.getDomains().get(0).startsWith("Designing and planning"));
    }

    @Test
    void withoutSectionMarkersFallsBackToTopicalBulletsMinusJunk() {
        CertificationExamMeta meta = parse("""
                <ul>
                  <li>Prepare with Certification Prep webinars Watch Cloud OnAir</li>
                  <li>Explore Google Cloud Skills Boost learning path</li>
                  <li>Schedule your exam for Google Cloud</li>
                  <li>Security and compliance in the cloud</li>
                  <li>Data analytics and machine learning on Google Cloud</li>
                </ul>
                """);

        assertEquals(List.of(
                "Security and compliance in the cloud",
                "Data analytics and machine learning on Google Cloud"), meta.getDomains());
    }

    @Test
    void junkMarkersDoNotRejectRealDomains() {
        assertTrue(CertificationGuidelinesFetcher.looksLikeJunk("Prepare with Certification Prep webinars Watch Cloud OnAir"));
        assertTrue(CertificationGuidelinesFetcher.looksLikeJunk("Register for the exam"));
        assertFalse(CertificationGuidelinesFetcher.looksLikeJunk("Managing data, networking and security on Google Cloud"));
        assertFalse(CertificationGuidelinesFetcher.looksLikeJunk("Training and serving machine learning models"));
        assertFalse(CertificationGuidelinesFetcher.looksLikeJunk("Section 3: Infrastructure and application modernization"));
    }

    @Test
    void parsesTwoHoursAndStandardRangeNotRenewal() {
        CertificationExamMeta meta = parse("""
                <h2>Standard exam information</h2>
                <p>Length: 2 hours</p>
                <p>Exam format: 50-60 multiple choice and multiple select questions</p>
                <h2>Renewal exam information</h2>
                <p>Length: 1 hour</p>
                <p>Exam format: 20 multiple choice and multiple select questions</p>
                """);
        assertEquals(120, meta.getDurationMinutes());
        assertEquals(50, meta.getQuestionCountMin());
        assertEquals(60, meta.getQuestionCountMax());
    }

    @Test
    void parsesWrittenHoursAndTildeCount() {
        CertificationExamMeta meta = parse("""
                <p>Length: Two hours</p>
                <p>Exam format: ~80 multiple choice questions</p>
                """);
        assertEquals(120, meta.getDurationMinutes());
        assertEquals(80, meta.getQuestionCountMin());
        assertEquals(80, meta.getQuestionCountMax());
    }

    @Test
    void parsesDataEngineerFortyToFifty() {
        CertificationExamMeta meta = parse("""
                <h2>Standard exam information</h2>
                <p>Length: 2 hours</p>
                <p>Exam format: 40-50 multiple choice and multiple select questions</p>
                <h2>Renewal exam information</h2>
                <p>Length: 1 hour. 20 multiple choice questions.</p>
                """);
        assertEquals(120, meta.getDurationMinutes());
        assertEquals(40, meta.getQuestionCountMin());
        assertEquals(50, meta.getQuestionCountMax());
    }

    @Test
    void doesNotInventCountsWhenPageHasNoExamSize() {
        CertificationExamMeta meta = parse("<p>The exam assesses your knowledge of cloud security.</p>");
        assertEquals(null, meta.getDurationMinutes());
        assertEquals(null, meta.getQuestionCountMin());
        assertEquals(null, meta.getQuestionCountMax());
    }
}
