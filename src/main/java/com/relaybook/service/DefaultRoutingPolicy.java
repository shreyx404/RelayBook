package com.relaybook.service;

import com.relaybook.model.Contact;

/**
 * The standard scoring used for person and organization contacts:
 * coverage 0/25/45/60 points, freshness 40/30/15/0 within the day bands.
 */
public final class DefaultRoutingPolicy extends AbstractRoutingPolicy {

    @Override
    protected int coveragePoints(Contact contact, int routeCount) {
        switch (routeCount) {
            case 0: return COVERAGE_NO_ROUTE;
            case 1: return COVERAGE_SINGLE_ROUTE;
            case 2: return COVERAGE_TWO_ROUTES;
            default: return COVERAGE_THREE_PLUS_ROUTES;
        }
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
            return FRESH_WITHIN_180_DAYS;
        }
        if (daysSinceVerified <= BAND_3_DAYS) {
            return FRESH_WITHIN_365_DAYS;
        }
        return STALE_OR_NEVER_VERIFIED;
    }
}
