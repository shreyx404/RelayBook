package com.relaybook.model;

import java.time.LocalDate;

/**
 * Specialized person: EmergencyContact -> PersonContact -> Contact demonstrates
 * multilevel inheritance, while PersonContact and OrganizationContact form a hierarchy.
 *
 * <p>Emergency records must be dependable at the worst moment, so they go stale
 * after only 90 days and require at least two distinct routes; the validator
 * rejects an emergency record that cannot offer a fallback.</p>
 */
public class EmergencyContact extends PersonContact {
    /** Emergency records are considered stale after 90 days. */
    public static final int STALE_AFTER_DAYS = 90;
    /** Emergency records must keep two routes so a fallback always exists. */
    public static final int MINIMUM_ROUTES = 2;

    /** Constructed from a builder; `super` reaches PersonContact then Contact. */
    public EmergencyContact(Builder builder) {
        super(builder);
    }

    @Override
    public ContactType getContactType() {
        return ContactType.EMERGENCY;
    }

    @Override
    public int getStaleAfterDays() {
        return STALE_AFTER_DAYS;
    }

    @Override
    public int getMinimumRoutes() {
        return MINIMUM_ROUTES;
    }

    @Override
    public String describe() {
        return "Emergency record for " + getFullName()
                + ". Emergency contacts are rechecked every " + getStaleAfterDays()
                + " days and must keep at least " + getMinimumRoutes()
                + " routes so a fallback is always available.";
    }
}
