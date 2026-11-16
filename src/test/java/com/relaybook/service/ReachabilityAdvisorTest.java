package com.relaybook.service;

import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The advisor must compare routes by their own verification dates and explain its choice. */
class ReachabilityAdvisorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private final ReachabilityAdvisor advisor = new ReachabilityAdvisor();

    private Contact contact(ContactType type, LocalDate phoneVerified, LocalDate emailVerified) {
        return Contact.builder(type).id("adv-1").fullName("Advice Contact")
                .preferredChannel(Channel.PHONE)
                .route(Channel.PHONE, "+91 10000 00000", phoneVerified)
                .route(Channel.EMAIL, "advice@example.test", emailVerified)
                .build();
    }

    @Test
    void stalePhoneAndFreshEmailSuggestsEmailWithPerRouteRationale() {
        Contact contact = contact(ContactType.PERSON, TODAY.minusDays(300), TODAY.minusDays(20));
        ReachabilityReport report = advisor.analyze(contact, TODAY);
        assertEquals(Channel.EMAIL, report.getSuggestedChannel());
        assertEquals("advice@example.test", report.getSuggestedValue());
        assertEquals("Needs verification", report.getStatus());
        String rationale = report.getRationale().toLowerCase(java.util.Locale.ROOT);
        assertTrue(rationale.contains("email was verified 20 days ago"),
                () -> "rationale: " + report.getRationale());
        assertTrue(rationale.contains("phone was verified 300 days ago"),
                () -> "rationale: " + report.getRationale());
        assertTrue(rationale.contains("try email first"),
                () -> "rationale: " + report.getRationale());
    }

    @Test
    void freshPreferredRouteIsSuggested() {
        Contact contact = contact(ContactType.PERSON, TODAY.minusDays(10), TODAY.minusDays(2));
        ReachabilityReport report = advisor.analyze(contact, TODAY);
        assertEquals(Channel.PHONE, report.getSuggestedChannel());
        assertEquals("Ready", report.getStatus());
        assertTrue(report.getRationale().contains("Preferred route is saved"));
    }

    @Test
    void neverVerifiedRouteLosesToADatedOne() {
        Contact contact = contact(ContactType.PERSON, null, TODAY.minusDays(40));
        ReachabilityReport report = advisor.analyze(contact, TODAY);
        assertEquals(Channel.EMAIL, report.getSuggestedChannel());
        assertTrue(report.getRationale().contains("Phone has never been verified"));
    }

    @Test
    void contactWithoutEnoughRoutesGetsTheBackupRouteStatus() {
        // Two dated routes but the emergency subtype demands two routes anyway -> Ready.
        Contact emergencyReady = contact(ContactType.EMERGENCY, TODAY.minusDays(3), TODAY.minusDays(3));
        assertEquals("Ready", advisor.analyze(emergencyReady, TODAY).getStatus());
        // A person with a single fresh route reports the single-route state.
        Contact singleRoute = Contact.builder(ContactType.PERSON).id("adv-3").fullName("Single")
                .route(Channel.PHONE, "+91 10000 00000", TODAY.minusDays(3)).build();
        assertEquals("Single route", advisor.analyze(singleRoute, TODAY).getStatus());
        assertTrue(advisor.analyze(singleRoute, TODAY).getRationale().contains("only saved route")
                || advisor.analyze(singleRoute, TODAY).getRationale().contains("Preferred route is saved"));
    }

    @Test
    void emergencyRecordGoesStaleSoonerThanAPersonRecordWithTheSameDates() {
        LocalDate old = TODAY.minusDays(120);
        Contact person = contact(ContactType.PERSON, old, null);
        Contact emergency = contact(ContactType.EMERGENCY, old, null);
        // 120 days is inside the person window (180) but outside the emergency window (90).
        assertEquals("Ready", advisor.analyze(person, TODAY).getStatus());
        assertEquals("Needs verification", advisor.analyze(emergency, TODAY).getStatus());
    }

    @Test
    void perRouteFreshnessIsExposedForTheUI() {
        Contact contact = contact(ContactType.PERSON, TODAY.minusDays(300), TODAY.minusDays(20));
        ReachabilityReport report = advisor.analyze(contact, TODAY);
        assertEquals(2, report.getRouteFreshness().size());
        assertEquals(Long.valueOf(300), report.getRouteFreshness().get(0).getDaysSinceVerified());
        assertEquals(Long.valueOf(20), report.getRouteFreshness().get(1).getDaysSinceVerified());
    }
}
