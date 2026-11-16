package com.relaybook.service;

import com.relaybook.model.Contact;

/**
 * Stricter scoring for emergency contacts: a single route is worth less
 * (they must keep two), and a record not checked within 180/365 days drops
 * much faster. The band edges stay identical to the default policy so results
 * stay comparable; only the point values differ.
 */
public final class StrictRoutingPolicy extends AbstractRoutingPolicy {

    @Override
    protected int coveragePoints(Contact contact, int routeCount) {
        if (routeCount == 0) {
            return COVERAGE_NO_ROUTE;
        }
        // An emergency contact below its own minimum (2 routes) is a weak record.
        if (routeCount < contact.getMinimumRoutes()) {
            return STRICT_COVERAGE_SINGLE_ROUTE;
        }
        if (routeCount == 2) {
            return COVERAGE_TWO_ROUTES;
        }
        return COVERAGE_THREE_PLUS_ROUTES;
    }

    @Override
    protected int freshnessPoints(long daysSinceVerified) {
        if (daysSinceVerified < 0) {
            return STALE_OR_NEVER_VERIFIED;
        }
        if (daysSinceVerified <= BAND_1_DAYS) {
            return FRESH_WITHIN_90_DAYS;
        }
        if (daysSinceVerified <= BAND_2_DAYS) {
            return STRICT_FRESH_WITHIN_180_DAYS;
        }
        if (daysSinceVerified <= BAND_3_DAYS) {
            return STRICT_FRESH_WITHIN_365_DAYS;
        }
        return STALE_OR_NEVER_VERIFIED;
    }
}
