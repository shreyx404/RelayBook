package com.relaybook.service;

import com.relaybook.model.Channel;
import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Score-band boundary tests at 90/91, 180/181, and 365/366 days for each
 * contact subtype and both routing policies, plus the strict/default differences.
 */
class RoutingPolicyBoundaryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private static Contact singleRoute(ContactType type, long daysSinceVerified) {
        Contact.Builder builder = Contact.builder(type).id("boundary").fullName("Boundary Contact")
                .route(Channel.PHONE, "+91 10000 00000",
                        daysSinceVerified < 0 ? null : TODAY.minusDays(daysSinceVerified));
        // An emergency record with one route is a defensive state (it cannot pass
        // validation today, but migrated legacy rows can look like this), so it is
        // constructed through the public subtype constructor, which does not validate.
        if (type == ContactType.EMERGENCY) {
            return new com.relaybook.model.EmergencyContact(builder);
        }
        return builder.build();
    }

    private static Contact twoRouteEmergency(long daysSinceVerified) {
        return Contact.builder(ContactType.EMERGENCY).id("boundary-2").fullName("Boundary Emergency")
                .preferredChannel(Channel.PHONE)
                .route(Channel.PHONE, "+91 10000 00000", TODAY.minusDays(daysSinceVerified))
                .route(Channel.EMAIL, "boundary@example.test", TODAY.minusDays(daysSinceVerified))
                .build();
    }

    private static int freshnessOfDefault(ContactType type, long days) {
        return new DefaultRoutingPolicy().evaluate(singleRoute(type, days), TODAY).getScore()
                - coverageOfDefault(type);
    }

    private static int coverageOfDefault(ContactType type) {
        return new DefaultRoutingPolicy().evaluate(singleRoute(type, -1), TODAY).getScore();
    }

    @Test
    void defaultPolicyFreshnessBandsAt90Days() {
        assertEquals(40, freshnessOfDefault(ContactType.PERSON, 90));
        assertEquals(30, freshnessOfDefault(ContactType.PERSON, 91));
        assertEquals(40, freshnessOfDefault(ContactType.ORGANIZATION, 90));
        assertEquals(30, freshnessOfDefault(ContactType.ORGANIZATION, 91));
    }

    @Test
    void defaultPolicyFreshnessBandsAt180Days() {
        assertEquals(30, freshnessOfDefault(ContactType.PERSON, 180));
        assertEquals(15, freshnessOfDefault(ContactType.PERSON, 181));
        assertEquals(30, freshnessOfDefault(ContactType.ORGANIZATION, 180));
        assertEquals(15, freshnessOfDefault(ContactType.ORGANIZATION, 181));
    }

    @Test
    void defaultPolicyFreshnessBandsAt365Days() {
        assertEquals(15, freshnessOfDefault(ContactType.PERSON, 365));
        assertEquals(0, freshnessOfDefault(ContactType.PERSON, 366));
        assertEquals(15, freshnessOfDefault(ContactType.ORGANIZATION, 365));
        assertEquals(0, freshnessOfDefault(ContactType.ORGANIZATION, 366));
    }

    @Test
    void strictPolicyUsesTheSameBandEdgesWithHarsherValues() {
        RoutingPolicy strict = new StrictRoutingPolicy();
        // Both routes share the target age, so the record-level date sits exactly on the band edge.
        assertEquals(85, strict.evaluate(twoRouteEmergency(90), TODAY).getScore());  // 45 + 40
        assertEquals(60, strict.evaluate(twoRouteEmergency(91), TODAY).getScore());  // 45 + 15
        assertEquals(60, strict.evaluate(twoRouteEmergency(180), TODAY).getScore()); // 45 + 15
        assertEquals(50, strict.evaluate(twoRouteEmergency(181), TODAY).getScore()); // 45 + 5
        assertEquals(50, strict.evaluate(twoRouteEmergency(365), TODAY).getScore()); // 45 + 5
        assertEquals(45, strict.evaluate(twoRouteEmergency(366), TODAY).getScore()); // 45 + 0
    }

    @Test
    void strictPolicyPenalisesALegacySingleRouteEmergency() {
        // A one-route emergency cannot be created through the validated builder today,
        // but rows migrated from the old flat schema can still look like this, so the
        // strict policy must keep scoring them low.
        Contact legacy = singleRoute(ContactType.EMERGENCY, 30);
        assertEquals(50, new StrictRoutingPolicy().evaluate(legacy, TODAY).getScore()); // 10 + 40
        assertEquals("Add a backup route", new StrictRoutingPolicy().evaluate(legacy, TODAY).getStatus());
    }

    @Test
    void staleStatusFollowsTheSubtypesOwnThreshold() {
        // Person: 180 fresh, 181 stale.
        assertEquals("Single route", new DefaultRoutingPolicy().evaluate(singleRoute(ContactType.PERSON, 180), TODAY).getStatus());
        assertEquals("Needs verification", new DefaultRoutingPolicy().evaluate(singleRoute(ContactType.PERSON, 181), TODAY).getStatus());
        // Organization: 365 fresh, 366 stale.
        assertEquals("Single route", new DefaultRoutingPolicy().evaluate(singleRoute(ContactType.ORGANIZATION, 365), TODAY).getStatus());
        assertEquals("Needs verification", new DefaultRoutingPolicy().evaluate(singleRoute(ContactType.ORGANIZATION, 366), TODAY).getStatus());
        // Emergency: 90 fresh (two dated routes, both at the edge), 91 stale.
        assertEquals("Ready", new StrictRoutingPolicy().evaluate(twoRouteEmergency(90), TODAY).getStatus());
        assertEquals("Needs verification", new StrictRoutingPolicy().evaluate(twoRouteEmergency(91), TODAY).getStatus());
    }

    @Test
    void strictPolicyScoresALegacySingleRouteEmergencyLowerThanTheDefaultPolicy() {
        Contact emergency = singleRoute(ContactType.EMERGENCY, 30);
        int defaultScore = new DefaultRoutingPolicy().evaluate(emergency, TODAY).getScore();
        int strictScore = new StrictRoutingPolicy().evaluate(emergency, TODAY).getScore();
        assertEquals(65, defaultScore); // 25 coverage + 40 freshness
        assertEquals(50, strictScore);  // 10 strict coverage + 40 freshness
        assertTrue(strictScore < defaultScore);
    }

    @Test
    void advisorPicksTheStrictPolicyForEmergencyContactsThroughItsRegistry() {
        ReachabilityAdvisor advisor = new ReachabilityAdvisor();
        assertEquals(50, advisor.analyze(singleRoute(ContactType.EMERGENCY, 30), TODAY).getScore());
        assertEquals(65, advisor.analyze(singleRoute(ContactType.PERSON, 30), TODAY).getScore());
    }
}
