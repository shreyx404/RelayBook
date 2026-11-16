package com.relaybook.service;

import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;

import java.time.LocalDate;

/**
 * Strategy: a scoring policy turns a contact into a {@link ReachabilityReport}.
 * The advisor delegates to the policy that belongs to the contact's type, so
 * emergency records can be judged by stricter rules without any if/else in the
 * calling code. All point values of every policy live here as named constants
 * so a viva can point at one place where the score is defined.
 */
public interface RoutingPolicy {
    /* Route coverage points (both policies). */
    int COVERAGE_NO_ROUTE = 0;
    int COVERAGE_SINGLE_ROUTE = 25;
    int COVERAGE_TWO_ROUTES = 45;
    int COVERAGE_THREE_PLUS_ROUTES = 60;

    /* Strict coverage: an emergency contact below its own minimum routes scores less. */
    int STRICT_COVERAGE_SINGLE_ROUTE = 10;

    /* Freshness points for the default policy (days since the latest check-in). */
    int FRESH_WITHIN_90_DAYS = 40;
    int FRESH_WITHIN_180_DAYS = 30;
    int FRESH_WITHIN_365_DAYS = 15;
    int STALE_OR_NEVER_VERIFIED = 0;

    /* Freshness points for the strict policy: the same band edges, harsher values. */
    int STRICT_FRESH_WITHIN_180_DAYS = 15;
    int STRICT_FRESH_WITHIN_365_DAYS = 5;

    /* Band edges shared by every policy; the staleness threshold itself comes from the contact. */
    int BAND_1_DAYS = 90;
    int BAND_2_DAYS = 180;
    int BAND_3_DAYS = 365;
    int MAX_SCORE = 100;

    /** Scores the contact as of the given date; implementations must be side-effect free. */
    ReachabilityReport evaluate(Contact contact, LocalDate today);

    /** The policy that belongs to this contact's subtype (emergency is strict). */
    static RoutingPolicy forContact(Contact contact) {
        return contact.getContactType() == ContactType.EMERGENCY
                ? new StrictRoutingPolicy()
                : new DefaultRoutingPolicy();
    }
}
